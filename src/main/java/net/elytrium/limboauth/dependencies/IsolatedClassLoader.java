package net.elytrium.limboauth.dependencies;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * A custom {@link URLClassLoader} designed to load classes in isolation. It prioritizes loading
 * from its own URLs before delegating to the parent classloader, effectively isolating the loaded
 * classes from the main application's classpath. This is particularly useful for managing different
 * versions of libraries or JDBC drivers.
 */
public class IsolatedClassLoader extends URLClassLoader {

  /**
   * Constructs an IsolatedClassLoader with the specified URLs and a parent classloader set to the
   * system classloader's parent (usually the bootstrap classloader), promoting isolation.
   *
   * @param urls The URLs from which to load classes and resources.
   */
  /** Default constructor. */
  public IsolatedClassLoader(URL[] urls) {
    super(urls, ClassLoader.getSystemClassLoader().getParent());
  }

  static {
    // Enables parallel class loading capabilities if supported by the JVM.
    ClassLoader.registerAsParallelCapable();
  }
}
