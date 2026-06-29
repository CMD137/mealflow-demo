# Meal App Legacy Demo

`meal-app` is a legacy in-memory demo client kept for local scenario playback only.

The real MealFlow system is the microservice set wired by `docker-compose.yml`:
`meal-gateway`, `meal-auth-user`, `meal-merchant`, `meal-catalog`, `meal-cart`,
`meal-order`, `meal-queue`, `meal-promotion`, `meal-payment`,
`meal-fulfillment`, and `meal-notify`.

This module is intentionally excluded from the default Maven reactor so it does
not look like part of the production microservice topology.

Run it only when you need the old single-process demo:

```powershell
mvn -Plegacy-demo -pl meal-app -am test
mvn -Plegacy-demo -pl meal-app -am spring-boot:run
```
