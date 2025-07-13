 # Leitura dos dados do topico

 Ler os dados do tópico com o consumer do Kafka

# Passo a passo para execução

## 1 - Acessar a console do broker

```bash
docker exec -it broker bash
```

## 2 - Executar o comando

 ```bash
kafka-console-consumer --bootstrap-server localhost:9092 \
 --topic sales-transactions \
 --from-beginning
```