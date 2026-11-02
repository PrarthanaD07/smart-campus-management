# Smart Campus Integrated Management System

A Java 17 terminal application for managing students, faculty, courses, campus resources, activities, bookings, and course enrollments. Records are stored in SQLite through JDBC and managed from interactive numbered menus.

## Requirements

- Java 17 or newer
- Maven 3.8 or newer

## Run the console application

The default entry point opens the terminal menu:

```powershell
mvn compile exec:java
```

The application creates `data/campus.db` on first run. The SQLite JDBC driver is downloaded by Maven. To use a different database location, set `CAMPUS_DB_URL` before starting the application:

```powershell
$env:CAMPUS_DB_URL = "jdbc:sqlite:C:/path/to/campus.db"
mvn compile exec:java
```

Use `0` in a menu to go back or exit. The console supports add, view, search, update, and delete for students, faculty, courses, resources, and activities; booking and enrollment menus provide their corresponding create, view, search, and remove/cancel operations.

## Optional legacy website

The project also retains its separately deployed browser version. It is not the default application entry point. The Render Docker image starts `edu.smartcampus.WebMain`; running `mvn compile exec:java` starts the console application instead. The hosted website is a separate web deployment and still requires Google OAuth credentials.

For the existing Render deployment, the `render.yaml` Blueprint binds to Render's `PORT` and stores SQLite in the container's temporary filesystem, so data may be lost after restarts or redeploys. Google OAuth must be configured in Render's environment for website sign-in; keep the client secret private.

```powershell
$env:GOOGLE_CLIENT_ID = "your-client-id"
$env:GOOGLE_CLIENT_SECRET = "your-client-secret"
$env:GOOGLE_REDIRECT_URI = "https://smart-campus-management-x1lv.onrender.com/auth/google/callback"
mvn exec:java -Dexec.mainClass=edu.smartcampus.WebMain
```

## Features

- Console CRUD menus for students, faculty, courses, resources, and campus activities
- Booking management with overlap checks and student/faculty validation
- Course enrollment management with duplicate assignment checks
- Student CSV export and a persistent activity log
- Inheritance and polymorphism for people and campus resource types
- Interfaces, generic JDBC repositories, collections, lambdas, and Streams
- Validated input and explicit exception handling

Bookings use local date/time in the format `yyyy-MM-dd HH:mm` and must have an end time later than the start time.
