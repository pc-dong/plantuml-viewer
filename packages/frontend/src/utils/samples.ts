export const TEMPLATE_CLASS_DIAGRAM = `@startuml
class ClassName1 {
  +field1: String
  +method1(): void
}

class ClassName2 {
  +field2: int
  +method2(): boolean
}

ClassName1 --> ClassName2 : relationship
@enduml`;

export const TEMPLATE_SEQUENCE_DIAGRAM = `@startuml
participant "Participant1" as p1
participant "Participant2" as p2
participant "Participant3" as p3

p1 -> p2 : message
p2 --> p3 : response
@enduml`;

export const SAMPLE_CLASS_DIAGRAM = `@startuml
skinparam classAttributeIconSize 0

class User {
  +name: String
  +email: String
  +login(): void
  +logout(): void
}

class Order {
  +id: Long
  +status: String
  +total: Double
  +create(): void
  +cancel(): void
}

class Product {
  +name: String
  +price: Double
  +stock: int
}

class PaymentService {
  +processPayment(order: Order): boolean
  +refund(order: Order): void
}

package "Shipping" {
  class ShippingService {
    +calculateShipping(order: Order): Double
    +trackShipment(orderId: Long): String
  }
}

interface NotificationService {
  +send(userId: Long, message: String): void
}

class EmailService implements NotificationService {
  +send(userId: Long, message: String): void
}

User "1" --> "*" Order : places
Order "1" --> "*" Product : contains
Order --> PaymentService : uses
Order --> ShippingService : uses
PaymentService ..> NotificationService : notifies
@enduml`;

export const SAMPLE_SEQUENCE_DIAGRAM = `@startuml
actor User
participant "Frontend" as FE
participant "API Gateway" as API
participant "Auth Service" as Auth
participant "Database" as DB

User -> FE : Login
FE -> API : POST /login
API -> Auth : Validate credentials
Auth -> DB : Query user
DB --> Auth : User data
Auth --> API : JWT token
API --> FE : 200 OK + token
FE --> User : Dashboard

User -> FE : View orders
FE -> API : GET /orders (Bearer token)
API -> Auth : Verify JWT
Auth --> API : Valid
API -> DB : Query orders
DB --> API : Order list
API --> FE : 200 OK + orders
FE --> User : Display orders
@enduml`;

export const SAMPLE_USECASE_DIAGRAM = `@startuml
left to right direction
actor Customer
actor Admin
actor System

rectangle "E-Commerce Platform" {
  usecase "Browse Products" as UC1
  usecase "Search Products" as UC2
  usecase "Add to Cart" as UC3
  usecase "Place Order" as UC4
  usecase "Make Payment" as UC5
  usecase "View Order History" as UC6
  usecase "Manage Inventory" as UC7
  usecase "Generate Reports" as UC8
}

Customer --> UC1
Customer --> UC2
Customer --> UC3
UC1 .> UC2 : extends
UC3 .> UC4 : includes
UC4 .> UC5 : includes
Customer --> UC6
Admin --> UC7
Admin --> UC8
@enduml`;
