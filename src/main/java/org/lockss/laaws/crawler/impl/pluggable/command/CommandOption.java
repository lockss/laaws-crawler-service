package org.lockss.laaws.crawler.impl.pluggable.command;

/**
 * Base class for all command line options
 */
public class CommandOption {
  String longKey;
  private String value;

  /**
   * Constructor.
   *
   * @param longKey A String with the long key of the command line option.
   */
  public CommandOption(String longKey) {
    super();
    this.longKey = longKey;
  }

  /**
   * Provides the long key of the command line option.
   *
   * @return a String with the long key of the command line option.
   */
  public String getLongKey() {
    return longKey;
  }

  /**
   * Provides the value of the command line option.
   *
   * @return a String with the value of the command line option.
   */
  public String getValue() {
    return value;
  }

  /**
   * Saves the value of the command line option.
   *
   * @param value A String with the value of the command line option.
   * @return a String with the value of the command line option.
   */
  public String setValue(String value) {
    this.value = value;
    return value;
  }
}
