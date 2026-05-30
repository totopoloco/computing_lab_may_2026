package at.mavila.computing_lab_may_2026.domain.arithmetic.exception;

/**
 * Thrown when a division by zero is attempted in the calculator domain.
 *
 * @author mavila
 * @since May 2026
 */
public class DivisionByZeroException extends RuntimeException {

  /**
   * Constructs a new {@code DivisionByZeroException} with a default message.
   */
  public DivisionByZeroException() {
    super("Division by zero is not allowed");
  }

}
