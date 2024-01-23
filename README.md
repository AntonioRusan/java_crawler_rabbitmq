# java_crawler_rabbitmq

**Сборщик на Java в связке с RabbitMQ**

Модуль, позволяющий создать сборщик, использующий очередь RabbitMQ для получения ссылок
и отправляя результат сбора в другую очередь.

Тестовый сборщик **product_spider** в проекте **test_project**:
* использует очередь **'java_crawl_orders'** для получения url вида https://demo-site.at.ispras.ru/product/{id};
* парсит страничку;
* пишет сообщения в виде json {'url','id','name'} в очередь **'java_crawl_results'**.

### Сборка проекта
Переменные для сборщика передаются в environment у javacrawler в dockerfile:
- `RABBITMQ_HOST=amqp://guest:guest@rabbitmq:5672/%2F` - ссылка для подключения к rabbitmq
- `RABBITMQ_INPUT_QUEUE_KEY=java_crawl_orders` - очередь входных сообщений
- `RABBITMQ_OUTPUT_QUEUE_KEY=java_crawl_results` - очередь сообщений результата работы сборщика

Необходимо сбилдить docker образ сборщика командой ```docker build -t javacrawler .```


### Тестирвание

Поднять сборщик и RabbitMQ: ```docker compose up -d```

Интерфейс RabbitMQ доступен по адресу: http://127.0.0.1:15672/

Для того, чтобы положить ссылки в очередь **'java_crawl_orders'** надо выполнить метод **main** в **Publisher**