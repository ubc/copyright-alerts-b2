package ca.ubc.ctlt.copyalerts.db.operations;

import java.util.Map;

import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;

public abstract class ScanProcessor
{
	/**
	 * @param result Key is the name of the data columns we're interested in.
	 * @throws InaccessibleDbException 
	 */
	abstract public void scan(Map<String, String> result) throws InaccessibleDbException;
	/**
	 * Since processors work on batch sizes, there might be stuff remaining
	 * in the batch that hasn't been processed, so we need a way to trigger
	 * processing for the unfinished batch.
	 * 
	 * @param wasInterrupted - Indicates whether the cleanup was triggered by an
	 * interruption or we actually run to completion
	 * @throws InaccessibleDbException 
	 */
	abstract public void cleanup(boolean wasInterrupted) throws InaccessibleDbException;
}
