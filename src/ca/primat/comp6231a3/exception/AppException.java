package ca.primat.comp6231a3.exception;

public class AppException extends java.lang.Exception {
	
    /**
	 * 
	 */
	private static final long serialVersionUID = 9127428641835637664L;

	/**
	 * Constructor
	 * 
	 * @param message
	 */
	public AppException(String message) {
        super(message);
    }
}