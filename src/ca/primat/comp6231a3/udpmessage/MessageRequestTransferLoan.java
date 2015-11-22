package ca.primat.comp6231a3.udpmessage;

import java.io.Serializable;

import ca.primat.comp6231a3.model.Account;
import ca.primat.comp6231a3.model.Loan;

/**
 * 
 * @author mat
 *
 */
public class MessageRequestTransferLoan implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public int sequenceNbr;
	public Loan loan = null;
	public Account account = null;
	
}
