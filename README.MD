[![Maven Central](https://img.shields.io/maven-central/v/ch.sourcepond.spring/spring-web-blueprint.svg)](https://maven-badges.herokuapp.com/maven-central/ch.sourcepond.spring/spring-web-blueprint)

# Quick Overview
This project enables the usage of the Spring-Web framework on OSGi Blueprint implementations other than Gemini Blueprint.
The project was initiated because there was no way to integrate the Blueprint-Container with the Spring-Web Application-Context
to resolve components and services from the Blueprint-Container. Currently, the project has the state of a working
prototype and has been successfully tested with a Spring-Web application on [Karaf](http://karaf.apache.org) with
[Apache Aries Blueprint](http://aries.apache.org/modules/blueprint.html).

## Usage
Add following configuration to your *web.xml*:
```
<servlet>
    <servlet-name>MyWebApp</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
    <init-param>
        <param-name>contextAttribute</param-name>
        <param-value>blueprintContext</param-value>
    </init-param>
</servlet>
```
