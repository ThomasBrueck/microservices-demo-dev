var express = require('express'),
  async = require('async'),
  pg = require('pg'),
  redis = require('redis'),
  path = require('path'),
  cookieParser = require('cookie-parser'),
  methodOverride = require('method-override'),
  app = express(),
  server = require('http').Server(app),
  io = require('socket.io')(server, {
    transports: ['polling']
  });

var port = process.env.PORT || 4000;

// Rate Limiting (in-process, per client IP). Default is higher to avoid breaking Socket.IO polling.
var rateLimitEnabled = (process.env.RATE_LIMIT_ENABLED || 'true').toLowerCase() !== 'false';
var rateLimitWindowMs = parseInt(process.env.RATE_LIMIT_WINDOW_MS || '60000', 10);
var rateLimitMax = parseInt(process.env.RATE_LIMIT_MAX || '300', 10);

function getClientIp(req) {
  var xff = req.headers['x-forwarded-for'];
  if (xff && typeof xff === 'string') {
    return xff.split(',')[0].trim();
  }
  return req.socket && req.socket.remoteAddress ? req.socket.remoteAddress : 'unknown';
}

var rateLimitState = new Map();
function rateLimiter(req, res, next) {
  if (!rateLimitEnabled) {
    return next();
  }
  var now = Date.now();
  var ip = getClientIp(req);
  var state = rateLimitState.get(ip);
  if (!state || now - state.windowStartMs >= rateLimitWindowMs) {
    state = { windowStartMs: now, count: 0 };
  }
  state.count += 1;
  rateLimitState.set(ip, state);

  if (state.count > rateLimitMax) {
    var retryAfterSeconds = Math.max(
      1,
      Math.ceil((rateLimitWindowMs - (now - state.windowStartMs)) / 1000)
    );
    res.set('Retry-After', String(retryAfterSeconds));
    return res.status(429).send('Too Many Requests');
  }
  return next();
}

// Cache-Aside (Redis): cache aggregated scores for a short TTL
var cacheEnabled = (process.env.CACHE_ENABLED || 'true').toLowerCase() !== 'false';
var redisUrl = process.env.REDIS_URL || 'redis://redis:6379';
var cacheTtlSeconds = parseInt(process.env.CACHE_TTL_SECONDS || '5', 10);
var cacheKeyScores = process.env.CACHE_KEY_SCORES || 'scores';
var redisClient = null;
var redisReady = false;

function initRedis() {
  if (!cacheEnabled) {
    return;
  }
  redisClient = redis.createClient({ url: redisUrl });
  redisClient.on('ready', function () {
    redisReady = true;
    console.log('Connected to redis');
  });
  redisClient.on('end', function () {
    redisReady = false;
  });
  redisClient.on('error', function () {
    redisReady = false;
  });

  redisClient.connect().catch(function (err) {
    redisReady = false;
    console.warn('Redis connect failed; continuing without cache:', err && err.message ? err.message : err);
  });
}

function getScoresFromCache(callback) {
  if (!cacheEnabled || !redisClient || !redisReady) {
    return callback(null, null);
  }
  redisClient
    .get(cacheKeyScores)
    .then(function (value) {
      return callback(null, value);
    })
    .catch(function () {
      return callback(null, null);
    });
}

function setScoresCache(value) {
  if (!cacheEnabled || !redisClient || !redisReady) {
    return;
  }
  redisClient.setEx(cacheKeyScores, cacheTtlSeconds, value).catch(function () {
    // Best-effort
  });
}

io.sockets.on('connection', function (socket) {
  socket.emit('message', { text: 'Welcome!' });

  socket.on('subscribe', function (data) {
    socket.join(data.channel);
  });
});

var pool = new pg.Pool({
  user: process.env.POSTGRES_USER || process.env.DATABASE_USER || 'okteto',
  password: process.env.POSTGRES_PASSWORD || process.env.DATABASE_PASSWORD || 'okteto',
  host: process.env.POSTGRES_HOST || process.env.DATABASE_HOST || 'postgresql',
  database: process.env.POSTGRES_DB || process.env.DATABASE_NAME || 'votes',
  port: process.env.POSTGRES_PORT || 5432,
  ssl: (process.env.DATABASE_SSL === 'true') ? { rejectUnauthorized: false } : false
});

async.retry(
  { times: 1000, interval: 1000 },
  function (callback) {
    pool.connect(function (err, client, done) {
      if (err) {
        console.error('Waiting for db', err);
      }
      callback(err, client);
    });
  },
  function (err, client) {
    if (err) {
      console.error('Giving up');
      return;
    }
    console.log('Connected to db');
    initRedis();
    getVotes(client);
  }
);

function getVotes(client) {
  getScoresFromCache(function (_cacheErr, cachedScores) {
    if (cachedScores) {
      io.sockets.emit('scores', cachedScores);
      return setTimeout(function () {
        getVotes(client);
      }, 1000);
    }

    client.query(
      'SELECT vote, COUNT(id) AS count FROM votes GROUP BY vote',
      [],
      function (err, result) {
        if (err) {
          console.error('Error performing query: ' + err);
        } else {
          var votes = collectVotesFromResult(result);
          var payload = JSON.stringify(votes);
          io.sockets.emit('scores', payload);
          setScoresCache(payload);
        }

        setTimeout(function () {
          getVotes(client);
        }, 1000);
      }
    );
  });
}

function collectVotesFromResult(result) {
  var votes = { a: 0, b: 0 };

  result.rows.forEach(function (row) {
    votes[row.vote] = parseInt(row.count);
  });

  return votes;
}

app.use(cookieParser());
app.use(express.urlencoded({ extended: true }));
app.use(methodOverride('X-HTTP-Method-Override'));
app.use(rateLimiter);
app.use(function (req, res, next) {
  res.header('Access-Control-Allow-Origin', '*');
  res.header(
    'Access-Control-Allow-Headers',
    'Origin, X-Requested-With, Content-Type, Accept'
  );
  res.header('Access-Control-Allow-Methods', 'PUT, GET, POST, DELETE, OPTIONS');
  next();
});

app.use(express.static(__dirname + '/views'));

app.get('/', function (req, res) {
  res.sendFile(path.resolve(__dirname + '/views/index.html'));
});

server.listen(port, function () {
  var port = server.address().port;
  console.log('App running on port ' + port);
});
