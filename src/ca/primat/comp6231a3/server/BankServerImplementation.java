//package ca.primat.comp6231a3.server;
//
//import java.io.IOException;
//import java.text.ParseException;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.Set;
//import java.util.concurrent.Callable;
//import java.util.concurrent.ExecutionException;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.Future;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.TimeoutException;
//import java.util.concurrent.locks.Lock;
//import java.util.concurrent.locks.ReadWriteLock;
//import java.util.concurrent.locks.ReentrantReadWriteLock;
//import java.util.logging.FileHandler;
//import java.util.logging.Logger;
//import java.util.logging.SimpleFormatter;
//
//import javax.jws.WebService;
//import javax.jws.soap.SOAPBinding;
//
//import ca.primat.comp6231a3.IBankServer;
//import ca.primat.comp6231a3.exception.ValidationException;
//import ca.primat.comp6231a3.model.Account;
//import ca.primat.comp6231a3.model.Loan;
//import ca.primat.comp6231a3.model.ThreadSafeHashMap;
//import ca.primat.comp6231a3.udpmessage.MessageResponseLoanSum;
//import ca.primat.comp6231a3.udpmessage.MessageResponseTransferLoan;
//import dlms.GetLoanResponse;
//import dlms.OpenAccountResponse;
//import dlms.ServerResponse;
//
///**
// * The Java implementation of the BankServer IDL interface
// * 
// * @author mat
// *
// */
//
//@WebService(endpointInterface = "ca.primat.comp6231a2.server.BankServerImplementation")
//@SOAPBinding(style = SOAPBinding.Style.RPC)
//public class BankServerImplementation implements IBankServer {
//
//	protected volatile Bank bank;
//	protected volatile HashMap<String, Bank> bankCollection;
//	protected volatile Object lockObject;
//	private int sequenceNbr = 1;
//	private Logger logger = null;
//
//	private Thread udpListenerThread;
//	private UdpListener udpListener;
//	
//	private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
//	private final Lock nonExclusiveLock = readWriteLock.readLock();
//	private final Lock exclusiveLock = readWriteLock.writeLock();
//
//	/**
//	 * Constructor - Use inversion of control so that we manage creation of dependencies outside this class
//	 * 
//	 * @param reg The local registry where BankServers register to make themselves available to clients
//	 * @param bankCollection The collection of all banks available in the system
//	 * @param bankId The bank ID of the bank that this server is managing
//	 */
//	public BankServerImplementation(HashMap<String, Bank> bankCollection, String bankId, final Object lockObject) {
//
//		super();
//		this.bankCollection = bankCollection;
//		this.bank = bankCollection.get(bankId);
//		this.lockObject = lockObject;
//
//		// Set up the logger
//		this.logger = Logger.getLogger(this.bank.getTextId());  
//	    FileHandler fh;  
//	    try {
//	        fh = new FileHandler(this.bank.getTextId() + "-log.txt");  
//	        logger.addHandler(fh);
//	        SimpleFormatter formatter = new SimpleFormatter();  
//	        fh.setFormatter(formatter);  
//	        logger.info(this.bank.getTextId() + " logger started");  
//	    } catch (SecurityException e) {  
//	        e.printStackTrace();
//	        System.exit(1);
//	    } catch (IOException e) {  
//	        e.printStackTrace(); 
//	        System.exit(1);
//	    }
//
//	    // Start the bank's UDP listener
//		logger.info(this.bank.getTextId() + ": Starting UDP Listener");
//		udpListener = new UdpListener(this.bank, this.logger);
//		udpListenerThread = new Thread(udpListener);
//		udpListenerThread.start();
//	}
//
//
//	public String printCustomerInfo() {
//
//		logger.info("-------------------------------\n" + this.bank.getTextId() + ": Client invoked printCustomerInfo()");
//
//		String result = new String();
//		result = "------ ACCOUNTS ------\n";
//		
//		exclusiveLock.lock();
//		try {
//
//			for (String key : this.bank.accounts.keySet()) {
//				ThreadSafeHashMap<Integer, Account> accountsByLetter = this.bank.accounts.get(key);
//				for (Integer accountId : accountsByLetter.keySet()) {
//					Account account = accountsByLetter.get(accountId);
//					result += account.toString() + "\n";
//				}
//			}
//			
//			result += "------ LOANS ------\n";
//			for (String key : this.bank.loans.keySet()) {
//				ThreadSafeHashMap<Integer, Loan> loansByLetter = this.bank.loans.get(key);
//				for (Integer loanId : loansByLetter.keySet()) {
//					Loan loan = loansByLetter.get(loanId);
//					result += loan.toString() + "\n";
//				}
//			}
//			
//		} finally {
//			exclusiveLock.unlock();
//		}
//
//		return result;
//	}
//
//	public OpenAccountResponse openAccount(String firstName, String lastName, String emailAddress, String phoneNumber,
//			String password) {
//
//		OpenAccountResponse resp = new OpenAccountResponse(false, "", "", 0);
//
//		logger.info("-------------------------------\n" + this.bank.getTextId() + ": Client invoked openAccount(emailAddress:" + emailAddress + ")");
//		
//		nonExclusiveLock.lock();
//		try {
//			resp.accountNbr = this.bank.createAccount(firstName, lastName, emailAddress, phoneNumber, password);
//		} catch (ValidationException e) {
//			resp.message = e.getMessage();
//			return resp;
//		} finally {
//			nonExclusiveLock.unlock();
//		}
//
//		if (resp.accountNbr > 0) {
//			logger.info(this.bank.getTextId() + " successfully opened an account for user " + emailAddress + " with account number " + resp.accountNbr);
//			resp.result = true;
//			resp.message = "Account " + emailAddress + "successfully created with account number " + resp.accountNbr;
//			return resp;
//		}
//		
//		logger.info(this.bank.getTextId() + " failed to open an account for user " + emailAddress);
//		resp.message = "Failed to open an account for user " + emailAddress;
//		return resp;
//	}
//
//
//	public GetLoanResponse getLoan(int accountNbr, String password, int requestedLoanAmount) {
//
//		int newLoanId = 0; 
//		int externalLoanSum = 0; // The total sum of loans at other banks for accountNbr
//		Object lock = null;
//		Account account; // The account corresponding to the account number
//		GetLoanResponse response = new GetLoanResponse(false, "", "", newLoanId);
//		ExecutorService pool;
//		Set<Future<MessageResponseLoanSum>> set;
//		
//		logger.info("-------------------------------\n" + this.bank.getTextId() + ": Client invoked getLoan(accountNbr:"
//				+ accountNbr + ", password:" + password + ", requestedLoanAmount:" + requestedLoanAmount + ")");
//
//		// We need to get the lock object from the account number, so essentially, 
//		// we're testing for account existence
//		account = this.bank.getAccount(accountNbr);
//		if (account == null) {
//			response.message = "Loan refused at bank " + this.bank.getId() + ". Account " + accountNbr
//					+ " does not exist.";
//			logger.info(this.bank.getTextId() + ": " + response.message);
//			return response;
//		}
//
//		this.bank.contextEmailAddress =  account.getEmailAddress();
//
//		lock = this.bank.getLockObject(account.getEmailAddress());
//
//		// Prepare the threads to call other banks to get the loan sum for this account
//		pool = Executors.newFixedThreadPool(this.bankCollection.size()-1);
//	    set = new HashSet<Future<MessageResponseLoanSum>>();
//	    
//		nonExclusiveLock.lock();
//		
//		try {
//			
//			synchronized (lock) {
//	
//				// Test the existence of the account (again, now that we're in the critical section)
//				// The account could have gotten deleted just before the synchronized block
//				/*account = this.bank.getAccount(accountNbr);
//				if (account == null) {
//					response.message = "Loan refused at bank " + this.bank.getId() + ". Account " + accountNbr + " does not exist.";
//					logger.info(this.bank.getTextId() + ": " + response.message);
//					return response;
//				}*/
//				
//				// Validate that passwords match
//				if (!account.getPassword().equals(password)) {
//					response.message = "Loan refused at bank " + this.bank.getId() + ". Invalid credentials.";
//					logger.info(this.bank.getTextId() + ": " + response.message);
//					return response;
//				}
//	
//				// Avoid making UDP requests if the loan amount is already bigger than the credit limit of the local account
//				int currentLoanAmount = this.bank.getLoanSum(account.getEmailAddress());
//				if (currentLoanAmount + requestedLoanAmount > account.getCreditLimit()) {
//					response.message = "Loan refused at bank " + this.bank.getId() + ". Local credit limit exceeded";
//					logger.info(this.bank.getTextId() + ": " + response.message);
//					return response;
//				}
//				
//				// Get the loan sum for all banks and approve or not the new loan
//			    for (Bank destinationBank : this.bankCollection.values()) {
//			    	if (this.bank != destinationBank) {
//						Callable<MessageResponseLoanSum> callable = new UdpGetLoanCallable(this.bank, destinationBank, account.getEmailAddress(), this.sequenceNbr, this.logger);
//						Future<MessageResponseLoanSum> future = pool.submit(callable);
//						set.add(future);
//					}
//				}
//	
//				for (Future<MessageResponseLoanSum> future : set) {
//		
//					try {
//						MessageResponseLoanSum loanSumResponse = future.get();
//						if (loanSumResponse == null) {
//							response.message = "Loan refused at bank " + this.bank.getId() + ". Unable to obtain a status for the original loan request.";
//							logger.info(this.bank.getTextId() + ": " + response.message);
//							return response;
//						}
//						else if (loanSumResponse.status) {
//							externalLoanSum += loanSumResponse.loanSum;
//						}
//						else {
//							response.message = "Loan refused at bank " + this.bank.getId() + ". " + loanSumResponse.message;
//							logger.info(this.bank.getTextId() + ": " + response.message);
//							return response;
//						}
//					} catch (InterruptedException e) {
//						e.printStackTrace();
//						response.message = "Loan request failed for user " + account.getEmailAddress() + ". InterruptedException";
//						logger.info(this.bank.getTextId() + ": " + response.message);
//						return response;
//						
//					} catch (ExecutionException e) {
//						e.printStackTrace();
//						response.message = "Loan request failed for user " + account.getEmailAddress() + ". ExecutionException";
//						logger.info(this.bank.getTextId() + ": " + response.message);
//						return response;
//					}
//				}
//	
//				pool.shutdown();
//				this.sequenceNbr++;
//				
//				// Refuse the loan request if the sum of all loans is greater than the credit limit
//				if ((requestedLoanAmount + externalLoanSum) > account.getCreditLimit()) {
//					response.message = "Loan refused at bank " + this.bank.getId() + ". Total credit limit exceeded.";
//					logger.info(this.bank.getTextId() + ": " + response.message);
//					return response;
//				}
//				
//				// Loan is approved at this point
//				newLoanId = this.bank.createLoan(account.getEmailAddress(), accountNbr, requestedLoanAmount);
//
//				response.result = true;
//				response.message = "Loan approved for user " + account.getEmailAddress() + ", amount " + requestedLoanAmount + " at bank " + this.bank.getId() + ".";
//				response.loanId = newLoanId;
//				logger.info(this.bank.getTextId() + ": " + response.message);
//
//				return response;
//			}
//		} finally {
//			this.bank.contextEmailAddress = "";
//			this.nonExclusiveLock.unlock();
//		}
//	}
//
//
//	public ServerResponse delayPayment(int loanId, String currentDueDate, String newDueDate) {
//		
//		logger.info("-------------------------------\n" + this.bank.getTextId() + ": Client invoked delayPayment(loanId:" + loanId + " currentDate: " + currentDueDate + " newDueDate: " + newDueDate +")");
//		
//		SimpleDateFormat dateFormat = new SimpleDateFormat("dd-M-yyyy");
//		//Date dateCurrent = null;
//		Date dateNew = null;
//		Loan loan;
//		Object lock;
//		
//		try {
//			//dateCurrent = dateFormat.parse(currentDueDate);
//			dateNew = dateFormat.parse(newDueDate);
//		} catch (ParseException e) {
//			e.printStackTrace();
//		}
//		
//		
//		loan = this.bank.getLoan(loanId);
//		if (loan == null) {
//			logger.info(this.bank.getTextId() + ": Loan id " + loanId + " does not exist");
//			return new ServerResponse(false, "", "Loan id " + loanId + " does not exist");
//		}
//		
//		lock = this.bank.getLockObject(loan.getEmailAddress());
//		
//		nonExclusiveLock.lock();
//		
//		try {
//		
//			synchronized (lock) {
//				
//				loan = this.bank.getLoan(loanId);
//				if (loan == null) {
//					logger.info(this.bank.getTextId() + ": Loan id " + loanId + " does not exist");
//					return new ServerResponse(false, "", "Loan id " + loanId + " does not exist");
//				}
//				// Uncomment when using date validation
//				/*if (!loan.getDueDate().equals(dateCurrent)) {
//					logger.info(this.bank.getTextId() + ": Loan id " + loanId + " - currentDate argument mismatch");
//					return new ServerResponse(false, "", "Loan id " + loanId + " - currentDate argument mismatch");
//				}*/
//				if (!loan.getDueDate().before(dateNew)) {
//					logger.info(this.bank.getTextId() + ": Loan id " + loanId + " - currentDueDate argument must be later than the actual current due date of the loan");
//					return new ServerResponse(false, "", " Loan id " + loanId + " - currentDueDate argument must be later than the actual current due date of the loan");
//				}
//				
//				loan.setDueDate(dateNew);
//			}	
//			
//		} finally {
//			nonExclusiveLock.unlock();
//		}
//
//		logger.info(this.bank.getTextId() + " loan " + loanId + " successfully delayed");
//		return new ServerResponse(true, this.bank.getTextId() + " loan " + loanId + " successfully delayed", "");
//	}
//
//
//	public ServerResponse transferLoan(int loanId, String currentBankId, String otherBankId) {
//
//		Bank destinationBank = this.getBankFromCollection(otherBankId);
//		
//		// 
//		synchronized (lockObject) {
//			
//			// Make sure the loan exists before creating the thread and UDP request
//			Loan loan = this.bank.getLoan(loanId);
//			if (loan == null) {
//				logger.info(this.bank.getTextId() + ": Loan transfer " + loanId + " failed. LoanId does not exist");
//				return new ServerResponse(false, "", "Loan transfer " + loanId + " failed. LoanId does not exist");
//			}
//
//			ExecutorService executor = Executors.newSingleThreadExecutor();
//			UdpTransferLoanCallable callable = new UdpTransferLoanCallable(this.getBank(), destinationBank, loanId, this.sequenceNbr, this.logger);
//			Future<MessageResponseTransferLoan> future = executor.submit(callable);
//			
//			try {
//				MessageResponseTransferLoan resp = future.get(5, TimeUnit.SECONDS);
//				if (resp.status) {
//					
//					// Loan transfered successfully. It must now be deleted locally.
//					this.bank.deleteLoan(loanId);
//					
//					logger.info(this.bank.getTextId() + ": Loan transfer " + loanId + " from " + currentBankId + " to " + otherBankId + " successful");
//					return new ServerResponse(true, "", "Loan transfer " + loanId + " from " + currentBankId + " to " + otherBankId + " successful");
//				}
//				else {
//					logger.info(this.bank.getTextId() + ": Loan transfer failed. " + resp.message);
//					return new ServerResponse(true, "", "Loan transfer failed. " + resp.message);
//				}
//			} catch (ExecutionException ee) {
//				System.err.println("Callable threw an execution exception: " + ee.getMessage());
//				System.exit(1);
//			} catch (InterruptedException e) {
//				System.err.println("Callable was interrupted: " + e.getMessage());
//				System.exit(1);
//			} catch (TimeoutException e) {
//				System.err.println("Callable transfer loan timed out: " + e.getMessage());
//				System.exit(1);
//			}
//			
//			executor.shutdown();
//		}
//
//		logger.info(this.bank.getTextId() + ": Loan transfer failed.");
//		return new ServerResponse(true, "", "Loan transfer failed.");
//	}
//	
//	//
//	// Getters and setters
//	//
//	
//	/**
//	 * 
//	 * @return
//	 */
//	public Bank getBank() {
//		return this.bank;
//	}
//
//	/**
//	 * 
//	 * @return
//	 */
//	protected Bank getBankFromCollection(String id) {
//	    for (Bank bank : this.bankCollection.values()) {
//	    	if (bank.getId().equals(id)) {
//				return bank;
//			}
//	    }
//	    return null;
//	}
//
//
//	@Override
//	public String printCustomerInfo(String bankId) {
//		// TODO Auto-generated method stub
//		return null;
//	}
//}
//
