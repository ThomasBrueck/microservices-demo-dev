package main

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"os"
	"os/signal"
	"strings"
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

func getEnvOrDefault(key, fallback string) string {
	if value, exists := os.LookupEnv(key); exists {
		return value
	}
	return fallback
}

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

	if os.Getenv("KAFKA_SECURITY_PROTOCOL") == "SASL_SSL" {
		config.Net.TLS.Enable = true
		config.Net.SASL.Enable = true
		config.Net.SASL.Mechanism = sarama.SASLTypePlaintext
		config.Net.SASL.User = os.Getenv("KAFKA_SASL_USERNAME")
		config.Net.SASL.Password = os.Getenv("KAFKA_SASL_PASSWORD")
	}

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
	dbHost := getEnvOrDefault("POSTGRES_HOST", getEnvOrDefault("DATABASE_HOST", "postgresql"))
	dbPort := getEnvOrDefault("POSTGRES_PORT", "5432")
	dbUser := getEnvOrDefault("POSTGRES_USER", getEnvOrDefault("DATABASE_USER", "okteto"))
	dbPass := getEnvOrDefault("POSTGRES_PASSWORD", getEnvOrDefault("DATABASE_PASSWORD", "okteto"))
	dbName := getEnvOrDefault("POSTGRES_DB", getEnvOrDefault("DATABASE_NAME", "votes"))

	sslMode := "disable"
	if getEnvOrDefault("DATABASE_SSL", "false") == "true" {
		sslMode = "require"
	}

	psqlconn := fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=%s", dbHost, dbPort, dbUser, dbPass, dbName, sslMode)
	for {
		db, err := sql.Open("postgres", psqlconn)
		if err == nil {
			return db
		}
		time.Sleep(1 * time.Second)
	}
}

func pingDatabase(db *sql.DB) {
	fmt.Println("Waiting for postgresql...")
	for {
		if err := db.Ping(); err == nil {
			fmt.Println("Postgresql connected!")
			return
		}
		time.Sleep(1 * time.Second)
	}
}


func getKafkaConsumerGroup(config *sarama.Config) sarama.ConsumerGroup {
	var brokers []string
	if b := os.Getenv("KAFKA_BOOTSTRAP_SERVERS"); b != "" {
		brokers = strings.Split(b, ",")
	} else {
		brokers = *brokerList
	}
	
	groupID := *consumerGroupID
	fmt.Println("Waiting for kafka...")
	for {
		group, err := sarama.NewConsumerGroup(brokers, groupID, config)
		if err != nil {
			fmt.Printf("Kafka connect error: %v\n", err)
			time.Sleep(1 * time.Second)
			continue
		}
		fmt.Println("Kafka connected!")
		return group
	}
}
