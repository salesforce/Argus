ArgusCore
=====

ArgusCore encapsulates the core Argus service implementations.  The ArgusCore artifact is a reusable system object that exposes the rudimentary services which is included as a dependency for ArgusWebServices and ArgusClient.

It handles loading Argus system configuration, binding service implementations and starting and stopping the Argus system.  The following example illustrates how a servlet context listener would create an instance of ArgusCore as well as starting and stopping it.

```java
  /* Actual code would probably want to add an interlock between startup and shutdown as 
   * well as synchronizing static access to the system object */
  private static SystemMain _system;
  
  public static SystemMain getSystem() {
    return _system;
  }
  
  @Override
  public void contextDestroyed(ServletContextEvent event) {
    _system.stop();
  }
  
  @Override
  public void contextInitialized(ServletContextEvent event) {
    _system = SystemMain.getInstance();
    _system.start();
  }
```

To find out more [see the wiki.](https://github.com/SalesforceEng/Argus/wiki)
