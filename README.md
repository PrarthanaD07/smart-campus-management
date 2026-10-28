# Smart Campus Integrated Management System

A responsive, colorful website and Java 17 application for managing students, faculty, courses, campus resources, activities, and room/equipment bookings. Website records can be viewed, searched, added, edited/updated, and deleted. Data is persisted in SQLite through JDBC.

## Requirements

- Java 17 or newer
- Maven 3.8 or newer

## Run

Website app (default):

```powershell
mvn compile exec:java
```

Then open `http://localhost:8080` in your browser. The site requires Google sign-in.

## Deploy to Render

The included `render.yaml` configures a free Docker web service and health check. SQLite is stored in the container's temporary filesystem, so database changes can be lost when Render restarts or redeploys the service. Choose a paid persistent disk if campus data must survive those events.

1. Push this project to a GitHub repository, then create a new **Blueprint** in Render and connect that repository. Render will use `render.yaml`; review the selected plan before confirming.
2. Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in the Render environment. These are prompted as secret values when creating the Blueprint or can be set later under the service's **Environment** settings.
3. This deployment's service URL is `https://smart-campus-management-x1lv.onrender.com`. Set `GOOGLE_REDIRECT_URI` in Render to `https://smart-campus-management-x1lv.onrender.com/auth/google/callback`.
4. In Google Cloud Console, register that exact HTTPS callback URL as an authorized redirect URI for the OAuth Web application client. Add your account as a test user if the consent screen is still in testing.
5. Redeploy after setting the environment values, then visit the Render service URL and sign in.

The service binds to Render's `PORT` on `0.0.0.0`. The included free configuration stores SQLite at `data/campus.db` in ephemeral container storage. Render environment variables are used for credentials; never add OAuth secrets to source control.

## Configure Google sign-in

1. In Google Cloud Console, create or select a project and configure the OAuth consent screen. While the app is in testing mode, add your Google account as a test user.
2. Create an OAuth client ID with application type **Web application**.
3. Add `http://localhost:8080/auth/google/callback` as an authorized redirect URI.
4. Set the client ID and secret in the same PowerShell window used to start the server:

   ```powershell
   $env:GOOGLE_CLIENT_ID = "your-client-id"
   $env:GOOGLE_CLIENT_SECRET = "your-client-secret"
   mvn compile exec:java
   ```

   Keep the client secret private. Do not put it in source files or commit it.

Optional settings:

```powershell
$env:GOOGLE_REDIRECT_URI = "http://localhost:8080/auth/google/callback"
$env:GOOGLE_ALLOWED_DOMAIN = "your-school.edu"
```

`GOOGLE_REDIRECT_URI` must exactly match the authorized redirect URI configured in Google Cloud. `GOOGLE_ALLOWED_DOMAIN` restricts sign-in to verified Google Workspace accounts in that domain. Sessions are held in memory and expire after 12 hours; restarting the server signs users out.

Legacy console app:

```powershell
mvn clean compile exec:java -Dexec.mainClass=edu.smartcampus.ConsoleMain
```

The application creates `data/campus.db` on first run. The SQLite JDBC driver is downloaded by Maven. To use a different database location, set `CAMPUS_DB_URL`, for example:

```powershell
$env:CAMPUS_DB_URL = "jdbc:sqlite:C:/path/to/campus.db"
mvn exec:java
```

## Features

- CRUD operations for students, faculty, courses, resources, campus activities, and bookings
- Responsive dashboard with colorful summaries and record tables
- Add, view, search, edit/update, and delete controls for each record type, including bookings and enrollments
- Student course enrollment and removal, with duplicate assignment checks
- Inheritance and polymorphism for people and campus resource types
- Interfaces, generic JDBC repositories, collections, lambdas, and Streams
- Validated input and explicit exception handling
- Synchronized booking checks to prevent overlapping bookings
- CSV export and a persistent activity log

Bookings use local date/time in the format `yyyy-MM-dd HH:mm` and must have an end time later than the start time.
