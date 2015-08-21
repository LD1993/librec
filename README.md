# librec
This project is a fork of [librec](https://github.com/guoguibing/librec/) and I made several improvements for my research insight.
### Management by Maven
As for me, who prefer to use __maven__, which is a project-oriented manager as well as __Idea Intellij__, naturally, I changed the project structure instead of using eclipse. Conveniently, run the command: ```mvn clean package```, and you will find the *jar* in the __target__ directory.

### Reading Internal Configurations
There are internal configurations when the parameters are default, *e.g.*, when running ```java -jar librec.jar```, it should read the cofiguration files inside, such as *librec.conf*, *log4j.xml*.
```java
InputStream configStream =
this.getClass().getClassLoader().getResourceAsStream(conf);
```
