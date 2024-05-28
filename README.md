# java_crawler_rabbitmq

**Сборщик на Java в связке с RabbitMQ**

Модуль, позволяющий создать сборщик, использующий очередь RabbitMQ для получения ссылок
и отправления результата сбора в другую очередь.

Тестовый сборщик **Crawler**:

* использует очередь **'java_crawl_requests'** для получения url вида https://demo-site.at.ispras.ru/product/{id};
* парсит страничку;
* если есть подзапросы, то отправляет их в очередь **'java_crawl_sub_requests'**;
* пишет сообщения в формате json в очередь **'java_crawl_results'**.

## Сборка проекта

### Сборка образа для docker-compose

Используется файл
Переменные для сборщика передаются в environment у javacrawler в Dockerfile:

- `RABBITMQ_HOST=amqp://guest:guest@rabbitmq:5672/%2F` - ссылка для подключения к rabbitmq
- `RABBITMQ_INPUT_CRAWL_REQUEST_QUEUE_KEY=java_crawl_requests` - очередь входных сообщений
- `RABBITMQ_OUTPUT_RESULT_QUEUE_KEY=java_crawl_results` - очередь сообщений результата работы сборщика
- `RABBITMQ_OUTPUT_CRAWL_REQUEST_QUEUE_KEY=java_crawl_sub_requests` - очередь выходных сообщений для подзаявок

Необходимо сбилдить docker образ сборщика командой ```docker build -f Dockerfile_build_image -t javacrawler .```

### Упаковка проекта в архив для сборки в kaniko

Чтобы упаковать jar сборщика и Dockerfile в архив надо:

- запустить скрипт `build_project.sh` - соберёт проект в jar-with-dependencies.jar файл
- выполнить скрипт `make_zip.sh` - упакует jar-файл и Dockerfile в zip архив

## Спецификация

### Формат входных сообщений заявок на сбор

```json
{
  "order_id": 1,
  "args": [
    {
      "key": "url",
      "value": "https://demo-site.at.ispras.ru/product/13"
    },
    {
      "key": "createSubRequests",
      "value": "true"
    }
  ]
}
```

### Формат выходных сообщений для результатов сбора

```json
{
  "order_id": 1,
  "status": "Finished",
  "result": {
    "product_id": "13",
    "product_name": "ThinkPad T540p",
    "url": "https://demo-site.at.ispras.ru/product/13"
  }
}
```

### Формат выходных сообщений для подзаявок

```json
{
  "order_id": 1,
  "args": [
    {
      "key": "url",
      "value": "https://demo-site.at.ispras.ru/product/55"
    },
    {
      "key": "createSubRequests",
      "value": "false"
    }
  ]
}
```

## Тестирвание

Поднять сборщик и RabbitMQ: ```docker compose up -d```

Интерфейс RabbitMQ доступен по адресу: http://127.0.0.1:15672/

Для того, чтобы положить ссылки в очередь **'java_crawl_requests'** надо выполнить метод **main** в **Publisher**