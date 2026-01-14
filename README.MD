# Spring Hotel Booking System 

Учебный проект — распределённая система бронирования отелей на базе  
**Spring Boot 3 / Spring Cloud**, реализованная в виде набора микросервисов.

Проект демонстрирует:
- микросервисную архитектуру;
- взаимодействие сервисов через API Gateway и Eureka;
- JWT-аутентификацию и разграничение ролей;
- идемпотентные операции и обработку повторных запросов;
- двухшаговую согласованность данных (Saga с компенсацией);
- сквозную корреляцию логов;
- базовый алгоритм равномерного распределения бронирований.



## Архитектура
```text
          Client
            |
            v
     API Gateway
 (Spring Cloud Gateway)
            |
      --------------
      |            |
      v            v
Booking Service  Hotel Service

      Eureka Server
   (Service Discovery)
```


### Компоненты

**Eureka Server**
  - Реестр сервисов 
  - Остальные сервисы регистрируются автоматически

**API Gateway**
  - Маршрутизация запросов
  - Прокси JWT-токена в backend-сервисы
  - Единая точка входа

**Booking Service**
  - Регистрация и аутентификация пользователей (JWT)
  - Создание и управление бронированиями
  - Реализация саги:
    - PENDING → CONFIRMED
    - PENDING → CANCELLED + compensation
  - Идемпотентная отмена бронирований 
  - Интеграция с Hotel Service
  - Сквозное логирование с correlationId

**Hotel Service**
  - CRUD отелей и номеров
  - Учёт статистики бронирований номеров (`timesBooked`)
  - Подтверждение и освобождение временных блокировок номеров


## Технологический стек

- Java 21
- Spring Boot 3.5.x
- Spring Cloud 2025.x
- Spring Cloud Gateway
- Spring Cloud Eureka
- Spring Security + JWT
- Spring Data JPA
- H2 (in-memory)
- WebClient
- SLF4J + MDC (correlationId)
- JUnit 5, MockMvc, WireMock
- Maven


## Безопасность

- JWT-аутентификация
- Роли:
    - **USER** — личные операции (бронирования)
    - **ADMIN** — управление пользователями, отелями и номерами
- Каждый сервис:
    - валидирует JWT самостоятельно (Resource Server)
- Корректные HTTP-статусы:
    - `401 Unauthorized`
    - `403 Forbidden`


## Алгоритм бронирования

1. **Booking Service**
    - создаёт бронирование со статусом `PENDING`
2. Запрашивает у **Hotel Service** подтверждение доступности номера
3. Возможные исходы:
    - Успех → `CONFIRMED`
    - Ошибка / конфликт / тайм-аут → `CANCELLED` + компенсация
Все переходы состояний логируются и коррелируются через correlationId

### Автоподбор номера

- Hotel Service хранит счётчик `timesBooked`
- Свободные номера сортируются:
  - по возрастанию `timesBooked`
  - при равенстве — по `id`
- Это позволяет более равномерно распределять нагрузку


## Основные эндпойнты

### Gateway

/api/bookings/** → Booking Service
/api/hotels/** → Hotel Service



### Booking Service

| Метод | URL | Роль | Описание |
|-----|-----|-----|---------|
| POST | /api/user/register | USER | Регистрация |
| POST | /api/user/auth | USER | Авторизация |
| POST | /api/booking | USER | Создание бронирования |
| GET | /api/bookings | USER | История бронирований |
| GET | /api/booking/{id} | USER | Получить бронирование |
| DELETE | /api/booking/{id} | USER | Отменить бронирование |
| POST | /api/user | ADMIN | Создать пользователя |
| PATCH | /api/user | ADMIN | Обновить пользователя |
| DELETE | /api/user | ADMIN | Удалить пользователя |


### Hotel Service

| Метод | URL                                           | Роль     | Описание |
|-----|-----------------------------------------------|----------|---------|
| POST | /api/hotels                                   | ADMIN    | Добавить отель |
| POST | /api/rooms                                    | ADMIN    | Добавить номер |
| GET | /api/hotels                                   | USER     | Список отелей |
| GET | /api/rooms                                    | USER     | Свободные номера |
| GET | /api/rooms/recommend                          | USER     | Рекомендованные номера |
| GET | /api/rooms/stats                              | ADMIN    | Статистика загруженности номеров |
| POST | /api/internal/rooms/{id}/confirm-availability | INTERNAL | Подтверждение доступности |
| POST | /api/internal/rooms/{id}/release              | INTERNAL | Компенсация |


## Структура БД

### Booking Service (H2)

- **users**
    - id
    - username
    - password
    - role

- **bookings**
    - id
    - user_id
    - room_id
    - start_date
    - end_date
    - status (PENDING / CONFIRMED / CANCELLED)
    - created_at

### Hotel Service (H2)

- **hotels**
    - id
    - name
    - address

- **rooms**
    - id
    - hotel_id
    - number
    - available
    - times_booked
    - 
- **room_holds**
    - id 
    - room_id 
    - request_id 
    - start_date 
    - end_date 
    - status (HOLD / RELEASED)


## Запуск проекта

1. Клонировать репозиторий:
```
git clone https://github.com/VictoriaMln/spring_final.git
```
Запустить сервисы в следующем порядке:

```
eureka-server
hotel-service
booking-service
api-gateway
```
Gateway будет доступен по адресу:

```
http://localhost:8080
```

## Тестирование
В проекте присутствуют:

- Unit-тесты (MockMvc)

- Интеграционные тесты

- WireMock для эмуляции Hotel Service

- Тесты параллельных бронирований

- Проверка компенсации при сбоях