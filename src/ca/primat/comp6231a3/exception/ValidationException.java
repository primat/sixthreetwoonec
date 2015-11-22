package ca.primat.comp6231a3.exception;

public class ValidationException extends AppException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 9117169278033384031L;

	/**
	 * Constructor
	 * 
	 * @param message
	 */
	public ValidationException(String message) {
		super(message);
	}
}
