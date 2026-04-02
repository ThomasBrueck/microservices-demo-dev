package main

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	_ "github.com/lib/pq"

	kingpin "github.com/alecthomas/kingpin/v2"

	"github.com/IBM/sarama"
)

var (
	brokerList        = kingpin.Flag("brokerList", "List of brokers to connect").Default("kafka:9092").Strings()
	topic             = kingpin.Flag("topic", "Topic name").Default("votes").String()
	consumerGroupID   = kingpin.Flag("group", "Kafka consumer group id").Default("vote-worker").String()
	messageCountStart = kingpin.Flag("messageCountStart", "Message counter start from:").Int()
)

const (
	host     = "postgresql"
	port     = 5432
	user     = "okteto"
	password = "okteto"
	dbname   = "votes"
)

func main() {
	kingpin.Parse()

	db := openDatabase()
	defer db.Close()

	pingDatabase(db)

	createTableStmt := `CREATE TABLE IF NOT EXISTS votes (id VARCHAR(255) PRIMARY KEY, vote VARCHAR(255) NOT NULL)`
	if _, err := db.Exec(createTableStmt); err != nil {
		log.Panic(err)
	}

	config := sarama.NewConfig()
	config.Version = sarama.MaxVersion
	config.Consumer.Return.Errors = true
	config.Consumer.Offsets.Initial = sarama.OffsetOldest

	group := getKafkaConsumerGroup(config)
	defer func() {
		_ = group.Close()
	}()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	signals := make(chan os.Signal, 1)
	signal.Notify(signals, os.Interrupt, syscall.SIGTERM)
	go func() {
		<-signals
		fmt.Println("Interrupt is detected")
		cancel()
	}()

	handler := &voteConsumerGroupHandler{db: db}
	processed := 0
	if messageCountStart != nil {
		processed = *messageCountStart
	}

	for {
		if ctx.Err() != nil {
			break
		}
		if err := group.Consume(ctx, []string{*topic}, handler); err != nil {
			log.Printf("consumer group error: %v", err)
			time.Sleep(500 * time.Millisecond)
		}
		select {
		case n := <-handler.processedCh:
			processed += n
		default:
		}
	}

	log.Println("Processed", processed, "messages")
}


type voteConsumerGroupHandler struct {
	db          *sql.DB
	processedCh chan int
}

func (h *voteConsumerGroupHandler) Setup(_ sarama.ConsumerGroupSession) error {
	if h.processedCh == nil {
		h.processedCh = make(chan int, 100)
	}
	return nil
}

func (h *voteConsumerGroupHandler) Cleanup(_ sarama.ConsumerGroupSession) error {
	return nil
}

func (h *voteConsumerGroupHandler) ConsumeClaim(session sarama.ConsumerGroupSession, claim sarama.ConsumerGroupClaim) error {
	for msg := range claim.Messages() {
		voterID := string(msg.Key)
		vote := string(msg.Value)
		if voterID == "" {
			// Skip messages with empty key to preserve one-vote-per-client semantics.
			session.MarkMessage(msg, "")
			continue
		}

		fmt.Printf("Received message: voter %s vote %s\n", voterID, vote)

		insertStmt := `insert into "votes"("id", "vote") values($1, $2) on conflict(id) do update set vote = $2`
		if _, err := h.db.Exec(insertStmt, voterID, vote); err != nil {
			return err
		}

		session.MarkMessage(msg, "")
		select {
		case h.processedCh <- 1:
		default:
		}
	}
	return nil
}

func openDatabase() *sql.DB {
	psqlconn := fmt.Sprintf("host=%s port=%d user=%s password=%s dbname=%s sslmode=disable", host, port, user, password, dbname)
	for {
		db, err := sql.Open("postgres", psqlconn)
		if err == nil {
			return db
		}
	}
}

func pingDatabase(db *sql.DB) {
	fmt.Println("Waiting for postgresql...")
	for {
		if err := db.Ping(); err == nil {
			fmt.Println("Postgresql connected!")
			return
		}
	}
}


func getKafkaConsumerGroup(config *sarama.Config) sarama.ConsumerGroup {
	brokers := *brokerList
	groupID := *consumerGroupID
	fmt.Println("Waiting for kafka...")
	for {
		group, err := sarama.NewConsumerGroup(brokers, groupID, config)
		if err != nil {
			continue
		}
		fmt.Println("Kafka connected!")
		return group
	}
}
