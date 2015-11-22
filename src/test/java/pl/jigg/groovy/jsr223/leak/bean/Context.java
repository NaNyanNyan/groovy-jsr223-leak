/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jigg.groovy.jsr223.leak.bean;

import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Jigga
 */
public class Context {
	
	private static final AtomicInteger GENERATOR = new AtomicInteger(0);
	
	private final Result result;
	private final String transactionId;

	public Context() {
		this.result = new Result();
		this.transactionId =
			String.format("transaction-%d", GENERATOR.incrementAndGet());
	}

	public Result getResult() {
		return result;
	}

	public String getTransactionId() {
		return transactionId;
	}
	
}
