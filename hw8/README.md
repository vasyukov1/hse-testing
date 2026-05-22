Запустить сервер можно одним из двух способов:

1. Docker контейнер

1.1. cd в папку с сервером  
1.2. docker build -t tradingboatmodel:v2.1 .  
1.3. docker run -p 8091:8091 tradingboatmodel:v2.1 

... profit, можно заходить в localhost:8091

2. простой прямой запуск

java -jar tradingboatmodel-0.2.1-SNAPSHOT.jar