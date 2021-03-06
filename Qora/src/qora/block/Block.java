package qora.block;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ntp.NTP;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import qora.BlockGenerator;
import qora.account.PublicKeyAccount;
import qora.crypto.Base58;
import qora.crypto.Crypto;
import qora.transaction.GenesisTransaction;
import qora.transaction.Transaction;
import qora.transaction.TransactionFactory;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import database.DBSet;

public class Block {
	
	public static final int MAX_BLOCK_BYTES = 1048576;
	public static final int VERSION_LENGTH = 4;
	public static final int REFERENCE_LENGTH = 128;
	public static final int TIMESTAMP_LENGTH = 8;
	public static final int GENERATING_BALANCE_LENGTH = 8;
	public static final int GENERATOR_LENGTH = 32;
	public static final int GENERATOR_SIGNATURE_LENGTH = 64;
	private static final int TRANSACTIONS_SIGNATURE_LENGTH = 64;
	private static final int TRANSACTIONS_COUNT_LENGTH = 4;
	private static final int TRANSACTION_SIZE_LENGTH = 4;
	private static final int BASE_LENGTH = VERSION_LENGTH + REFERENCE_LENGTH + TIMESTAMP_LENGTH + GENERATING_BALANCE_LENGTH + GENERATOR_LENGTH + TRANSACTIONS_SIGNATURE_LENGTH + GENERATOR_SIGNATURE_LENGTH + TRANSACTIONS_COUNT_LENGTH;
	public static final int MAX_TRANSACTION_BYTES = MAX_BLOCK_BYTES - BASE_LENGTH;
	
	protected int version;
	protected byte[] reference;
	protected long timestamp;
	protected long generatingBalance;
	protected PublicKeyAccount generator;
	protected byte[] generatorSignature;
	
	private List<Transaction> transactions;	
	private int transactionCount;
	private byte[] rawTransactions;
		
	protected byte[] transactionsSignature;
	
	public Block(int version, byte[] reference, long timestamp, long generatingBalance, PublicKeyAccount generator, byte[] generatorSignature)
	{
		this.version = version;
		this.reference = reference;
		this.timestamp = timestamp;
		this.generatingBalance = generatingBalance;
		this.generator = generator;
		this.generatorSignature = generatorSignature;
		
		this.transactionCount = 0;
	}
	
	//GETTERS/SETTERS
	
	public int getVersion()
	{
		return version;
	}
	
	public byte[] getGeneratorSignature()
	{
		return this.generatorSignature;
	}
	
	public long getTimestamp()
	{
		return this.timestamp;
	}
	
	public long getGeneratingBalance()
	{
		return this.generatingBalance;
	}
	
	public byte[] getReference()
	{
		return this.reference;
	}
	
	public PublicKeyAccount getGenerator()
	{
		return this.generator;
	}
	
	public BigDecimal getTotalFee()
	{
		BigDecimal fee = BigDecimal.ZERO.setScale(8);
		
		for(Transaction transaction: this.getTransactions())
		{
			fee = fee.add(transaction.getFee());
		}
		
		return fee;
	}
	
	public void setTransactionData(int transactionCount, byte[] rawTransactions)
	{
		this.transactionCount = transactionCount;
		this.rawTransactions = rawTransactions;
	}
	
	public int getTransactionCount() 
	{	
		return this.transactionCount;		
	}
	
	public synchronized List<Transaction> getTransactions() 
	{
		if(this.transactions == null)
		{
			//LOAD TRANSACTIONS
			this.transactions = new ArrayList<Transaction>();
					
			try
			{
				int position = 0;
				for(int i=0; i<transactionCount; i++)
				{
					//GET TRANSACTION SIZE
					byte[] transactionLengthBytes = Arrays.copyOfRange(this.rawTransactions, position, position + TRANSACTION_SIZE_LENGTH);
					int transactionLength = Ints.fromByteArray(transactionLengthBytes);
					
					//PARSE TRANSACTION
					byte[] transactionBytes = Arrays.copyOfRange(this.rawTransactions, position + TRANSACTION_SIZE_LENGTH, position + TRANSACTION_SIZE_LENGTH + transactionLength);
					Transaction transaction = TransactionFactory.getInstance().parse(transactionBytes);
					
					//ADD TO TRANSACTIONS
					this.transactions.add(transaction);
					
					//ADD TO POSITION
					position += TRANSACTION_SIZE_LENGTH + transactionLength;
				}
			}
			catch(Exception e)
			{
				//FAILED TO LOAD TRANSACTIONS
			}
		}
		
		return this.transactions;
	}
	
	public void addTransaction(Transaction transaction)
	{
		this.getTransactions().add(transaction);
		
		this.transactionCount++;
	}
	
	public Transaction getTransaction(byte[] signature) {
		
		for(Transaction transaction: this.getTransactions())
		{
			if(Arrays.equals(transaction.getSignature(), signature))
			{
				return transaction;
			}
		}
		
		return null;
	}
	
	public Block getParent()
	{
		return this.getParent(DBSet.getInstance());
	}
	
	public Block getParent(DBSet db)
	{
		return db.getBlockMap().get(this.reference);
	}
	
	public Block getChild()
	{
		return this.getChild(DBSet.getInstance());
	}
	
	public Block getChild(DBSet db)
	{
		return db.getChildMap().get(this);
	}
	
	public int getHeight()
	{
		return this.getHeight(DBSet.getInstance());
	}
	
	public int getHeight(DBSet db)
	{
		return db.getHeightMap().get(this);
	}
	
	public void setTransactionsSignature(byte[] transactionsSignature) 
	{
		this.transactionsSignature = transactionsSignature;	
	}
	
	public byte[] getSignature()
	{
		return Bytes.concat(this.generatorSignature, this.transactionsSignature);
	}
	
	//PARSE/CONVERT
	
	public static Block parse(byte[] data) throws Exception
	{
		//CHECK IF WE HAVE MINIMUM BLOCK LENGTH
		if(data.length < BASE_LENGTH)
		{
			throw new Exception("Data is less then minimum block length");
		}
		
		int position = 0;
		
		//READ VERSION
		byte[] versionBytes = Arrays.copyOfRange(data, position, position + VERSION_LENGTH);
		int version = Ints.fromByteArray(versionBytes);
		position += VERSION_LENGTH;

		//READ TIMESTAMP
		byte[] timestampBytes = Arrays.copyOfRange(data, position, position + TIMESTAMP_LENGTH);
		long timestamp = Longs.fromByteArray(timestampBytes);
		position += TIMESTAMP_LENGTH;		
		
		//READ REFERENCE
		byte[] reference = Arrays.copyOfRange(data, position, position + REFERENCE_LENGTH);
		position += REFERENCE_LENGTH;
		
		//READ GENERATING BALANCE
		byte[] generatingBalanceBytes = Arrays.copyOfRange(data, position, position + GENERATING_BALANCE_LENGTH);
		long generatingBalance = Longs.fromByteArray(generatingBalanceBytes);
		position += GENERATING_BALANCE_LENGTH;
		
		//READ GENERATOR
		byte[] generatorBytes = Arrays.copyOfRange(data, position, position + GENERATOR_LENGTH);
		PublicKeyAccount generator = new PublicKeyAccount(generatorBytes);
		position += GENERATOR_LENGTH;
		
		//READ TRANSACTION SIGNATURE
		byte[] transactionsSignature =  Arrays.copyOfRange(data, position, position + TRANSACTIONS_SIGNATURE_LENGTH);
		position += TRANSACTIONS_SIGNATURE_LENGTH;
				
		
		//READ GENERATOR SIGNATURE
		byte[] generatorSignature =  Arrays.copyOfRange(data, position, position + GENERATOR_SIGNATURE_LENGTH);
		position += GENERATOR_SIGNATURE_LENGTH;
		
		//CREATE BLOCK
		Block block = new Block(version, reference, timestamp, generatingBalance, generator, generatorSignature);
		
		//READ TRANSACTIONS COUNT
		byte[] transactionCountBytes = Arrays.copyOfRange(data, position, position + TRANSACTIONS_COUNT_LENGTH);
		int transactionCount = Ints.fromByteArray(transactionCountBytes);
		position += TRANSACTIONS_COUNT_LENGTH;
		
		//SET TRANSACTIONDATA
		byte[] rawTransactions = Arrays.copyOfRange(data, position, data.length);
		block.setTransactionData(transactionCount, rawTransactions);
		
		//SET TRANSACTIONS SIGNATURE
		block.setTransactionsSignature(transactionsSignature);
				
		return block;
	}
	
	@SuppressWarnings("unchecked")
	public JSONObject toJson()
	{
		JSONObject block = new JSONObject();
		
		block.put("version", this.version);
		block.put("reference", Base58.encode(this.reference));
		block.put("timestamp", this.timestamp);
		block.put("generatingBalance", this.generatingBalance);
		block.put("generator", this.generator.getAddress());
		block.put("fee", this.getTotalFee().toPlainString());
		block.put("transactionsSignature", Base58.encode(this.transactionsSignature));
		block.put("generatorSignature", Base58.encode(this.generatorSignature));
		block.put("signature",  Base58.encode(this.getSignature()));
		
		//CREATE TRANSACTIONS
		JSONArray transactionsArray = new JSONArray();
		
		for(Transaction transaction: this.getTransactions())
		{
			transactionsArray.add(transaction.toJson());
		}
		
		//ADD TRANSACTIONS TO BLOCK
		block.put("transactions", transactionsArray);
		
		//RETURN
		return block;
	}

	public byte[] toBytes() 
	{
		byte[] data = new byte[0];
		
		//WRITE VERSION
		byte[] versionBytes = Ints.toByteArray(this.version);
		//versionBytes = Bytes.ensureCapacity(versionBytes, 4, 0);
		data = Bytes.concat(data, versionBytes);

		//WRITE TIMESTAMP
		byte[] timestampBytes = Longs.toByteArray(this.timestamp);
		timestampBytes = Bytes.ensureCapacity(timestampBytes, 8, 0);
		data = Bytes.concat(data, timestampBytes);
					
		//WRITE REFERENCE
		byte[] referenceBytes = Bytes.ensureCapacity(this.reference, REFERENCE_LENGTH, 0);
		data = Bytes.concat(data, referenceBytes);
		
		//WRITE GENERATING BALANCE
		byte[] baseTargetBytes = Longs.toByteArray(this.generatingBalance);
		//baseTargetBytes = Bytes.ensureCapacity(baseTargetBytes, 8, 0);
		data = Bytes.concat(data,baseTargetBytes);
		
		//WRITE GENERATOR
		byte[] generatorBytes = Bytes.ensureCapacity(this.generator.getPublicKey(), GENERATOR_LENGTH, 0);
		data = Bytes.concat(data, generatorBytes);
		
		//WRITE TRANSACTIONS SIGNATURE
		data = Bytes.concat(data, this.transactionsSignature);
		
		//WRITE GENERATOR SIGNATURE
		data = Bytes.concat(data, this.generatorSignature);
		//WRITE TRANSACTION COUNT
		byte[] transactionCountBytes = Ints.toByteArray(this.getTransactionCount());
		//transactionCountBytes = Bytes.ensureCapacity(transactionCountBytes, 4, 0);
		data = Bytes.concat(data, transactionCountBytes);
		
		for(Transaction transaction: this.getTransactions())
		{
			//WRITE TRANSACTION LENGTH
			int transactionLength = transaction.getDataLength();
			byte[] transactionLengthBytes = Ints.toByteArray(transactionLength);
			//transactionLengthBytes = Bytes.ensureCapacity(transactionLengthBytes, 4, 0);
			data = Bytes.concat(data, transactionLengthBytes);
			
			//WRITE TRANSACTION
			data = Bytes.concat(data, transaction.toBytes());
		}
		
		return data;
	}
	
	public int getDataLength() {
		
		int length = BASE_LENGTH;
		
		for(Transaction transaction: this.getTransactions())
		{
			length += 4 + transaction.getDataLength();
		}
		
		return length;
	}
	
	//VALIDATE

	public boolean isSignatureValid()
	{
		//VALIDATE BLOCK SIGNATURE
		byte[] data = new byte[0];
		
		//WRITE PARENT GENERATOR SIGNATURE
		byte[] generatorSignature = Arrays.copyOfRange(this.reference, 0, GENERATOR_SIGNATURE_LENGTH);
		data = Bytes.concat(data, generatorSignature);
		
		//WRITE GENERATING BALANCE
		byte[] baseTargetBytes = Longs.toByteArray(this.generatingBalance);
		data = Bytes.concat(data, baseTargetBytes);
		
		//WRITE GENERATOR
		byte[] generatorBytes = Bytes.ensureCapacity(this.generator.getPublicKey(), GENERATOR_LENGTH, 0);
		data = Bytes.concat(data, generatorBytes);
								
		if(!Crypto.getInstance().verify(this.generator.getPublicKey(), this.generatorSignature, data))
		{
			return false;
		}
		
		//VALIDATE TRANSACTIONS SIGNATURE
		data = this.generatorSignature;		
		for(Transaction transaction: this.getTransactions())
		{
			//CHECK IF TRANSACTION SIGNATURE IS VALID
			if(!transaction.isSignatureValid())
			{
				return false;
			}
			
			//ADD SIGNATURE TO DATA
			data = Bytes.concat(data, transaction.getSignature());
		}
		
		if(!Crypto.getInstance().verify(this.generator.getPublicKey(), this.transactionsSignature, data))
		{
			return false;
		}
		
		return true;
	}
	
	public boolean isValid()
	{
		return this.isValid(DBSet.getInstance());
	}
	
	public boolean isValid(DBSet db)
	{		
		//CHECK IF PARENT EXISTS
		if(this.reference == null || this.getParent(db) == null)
		{
			return false;
		}
		
		//CHECK IF TIMESTAMP IS VALID -500 MS ERROR MARGIN TIME
		if(this.timestamp - 500 > NTP.getTime() || this.timestamp < this.getParent(db).timestamp)
		{
			return false;
		}
		
		//CHECK IF TIMESTAMP REST SAME AS PARENT TIMESTAMP REST
		if(this.timestamp % 1000 != this.getParent(db).timestamp % 1000)
		{
			return false;
		}
		
		//CHECK IF GENERATING BALANCE IS CORRECT
		if(this.generatingBalance != BlockGenerator.getNextBlockGeneratingBalance(db, this.getParent(db)))
		{
			return false;
		}
		
		//CREATE TARGET
		byte[] targetBytes = new byte[32];
		Arrays.fill(targetBytes, Byte.MAX_VALUE);
		BigInteger target = new BigInteger(1, targetBytes);
		
		//DIVIDE TARGET BY BASE TARGET
		BigInteger baseTarget = BigInteger.valueOf(BlockGenerator.getBaseTarget(this.generatingBalance));
		target = target.divide(baseTarget);
		
		//MULTIPLY TARGET BY USER BALANCE
		target = target.multiply(this.generator.getGeneratingBalance(db).toBigInteger());
		
		//MULTIPLE TARGET BY GUESSES
		long guesses = (this.timestamp - this.getParent(db).getTimestamp()) / 1000;
		BigInteger lowerTarget = target.multiply(BigInteger.valueOf(guesses-1));
		target = target.multiply(BigInteger.valueOf(guesses));
		
		//HASH SIGNATURE
		byte[] hash = Crypto.getInstance().digest(this.generatorSignature);
		
		//CONVERT HASH TO BIGINT
		BigInteger hashValue = new BigInteger(1, hash);
		
		//CHECK IF HASH LOWER THEN TARGET
		if(hashValue.compareTo(target) >= 0)
		{
			return false;
		}
		
		//CHECK IF FIRST BLOCK OF USER	
		if(hashValue.compareTo(lowerTarget) < 0)
		{
			return false;
		}
		
		//CHECK TRANSACTIONS
		DBSet fork = db.fork();
		for(Transaction transaction: this.getTransactions())
		{
			//CHECK IF NOT GENESISTRANSACTION
			if(transaction instanceof GenesisTransaction)
			{
				return false;
			}
			
			//CHECK IF VALID
			if(transaction.isValid(fork) != Transaction.VALIDATE_OKE)
			{
				return false;
			}
			
			//CHECK TIMESTAMP AND DEADLINE
			if(transaction.getTimestamp() > this.timestamp || transaction.getDeadline() <= this.timestamp)
			{
				return false;
			}
			
			//PROCESS TRANSACTION IN MEMORYDB TO MAKE SURE OTHER TRANSACTIONS VALIDATE PROPERLY
			transaction.process(fork);		
		}
		
		//BLOCK IS VALID
		return true;
	}
	
	//PROCESS/ORPHAN
	
	public void process()
	{
		this.process(DBSet.getInstance());
	}
	
	public void process(DBSet db)
	{	
		//PROCESS TRANSACTIONS
		for(Transaction transaction: this.getTransactions())
		{
			//PROCESS
			transaction.process(db);
			
			//SET PARENT
			db.getTransactionParentMap().set(transaction, this);
			
			//REMOVE FROM UNCONFIRMED DATABASE
			db.getTransactionMap().delete(transaction);
		}
		
		//PROCESS FEE
		BigDecimal blockFee = this.getTotalFee();
		if(blockFee.compareTo(BigDecimal.ZERO) == 1)
		{
			//UPDATE GENERATOR BALANCE WITH FEE
			this.generator.setConfirmedBalance(this.generator.getConfirmedBalance(db).add(blockFee), db);
		}
				
		Block parent = this.getParent(db);
		if(parent != null)
		{
			//SET AS CHILD OF PARENT
			db.getChildMap().set(parent, this);		
			
			//SET BLOCK HEIGHT
			int height = parent.getHeight(db) + 1;
			db.getHeightMap().set(this, height);
		}
		else
		{
			//IF NO PARENT HEIGHT IS 1
			db.getHeightMap().set(this, 1);
		}
		
		//ADD TO DB
		db.getBlockMap().add(this);
				
		//UPDATE LAST BLOCK
		db.getBlockMap().setLastBlock(this);	
	}
	
	public void orphan()
	{	
		this.orphan(DBSet.getInstance());
	}
	
	public void orphan(DBSet db)
	{
		//ORPHAN TRANSACTIONS
		this.orphanTransactions(this.getTransactions(), db);
				
		//REMOVE FEE
		BigDecimal blockFee = this.getTotalFee();
		if(blockFee.compareTo(BigDecimal.ZERO) == 1)
		{
			//UPDATE GENERATOR BALANCE WITH FEE
			this.generator.setConfirmedBalance(this.generator.getConfirmedBalance(db).subtract(blockFee), db);
		}
				
		//DELETE BLOCK FROM DB
		db.getBlockMap().delete(this);
				
		//SET PARENT AS LAST BLOCK
		db.getBlockMap().setLastBlock(this.getParent(db));
				
		//ADD ORPHANED TRANASCTIONS BACK TO DATABASE
		for(Transaction transaction: this.getTransactions())
		{
			db.getTransactionMap().add(transaction);
		}
	}
	
	private void orphanTransactions(List<Transaction> transactions, DBSet db)
	{
		//ORPHAN ALL TRANSACTIONS IN DB BACK TO FRONT
		for(int i=transactions.size() -1; i>=0; i--)
		{
			Transaction transaction = transactions.get(i);
			transaction.orphan(db);
		}
	}
	
}
