/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jigg.groovy.jsr223.leak;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static java.util.concurrent.ThreadLocalRandom.current;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.jigg.groovy.jsr223.leak.bean.Context;
import pl.jigg.groovy.jsr223.leak.bean.Result;

/**
 *
 * @author Jigga
 */
public class LeakTest {
	
	private static final Logger LOGGER =
		LoggerFactory.getLogger(LeakTest.class);
	
	private static final ScriptEngineManager SCRIPT_ENGINE_MANAGER =
		new ScriptEngineManager(LeakTest.class.getClassLoader());
	
	private static final String GROOVYSCRIPT =
		"\ndef execute(context) {\n" +
			"\tlogger.info('Inside execute function with no arguments...')\n" +
			"\tlogger.info('Random number is: %s.')\n" +
			"\tlogger.info('Context: {}.', ctx)\n" +
			"\tdef result = ctx.result\n" +
			"\tresult.transactionId = ctx.transactionId\n" +
			"\tresult.code = taskId\n" +
			"\tresult.path = 'executeWithoutArgs'\n" +
			"\tresult.message = 'Hello from script with no rollback function!'\n" +
			"\treturn result\n" +
		"}";
	
	private static final String JAVASCRIPT =
		"\nfunction execute(context) {\n" +
			"\tlogger.info('Inside execute function with no arguments...')\n" +
			"\tlogger.info('Random number is: %s.')\n" +
			"\tlogger.info('Context: {}.', ctx)\n" +
			"\tvar result = ctx.result\n" +
			"\tresult.transactionId = ctx.transactionId\n" +
			"\tresult.code = taskId\n" +
			"\tresult.path = 'executeWithoutArgs'\n" +
			"\tresult.message = 'Hello from script with no rollback function!'\n" +
			"\treturn result\n" +
		"}";
	
	private static final AtomicInteger COUNTER = new AtomicInteger(0);
	
	Callable<Result> scriptRunner = () -> {
		int taskId = COUNTER.incrementAndGet();
		LOGGER.info("Starting task {}...", taskId);
		String scriptSource, script;
		String language =
			System.getProperty("language");
		if ("javascript".equalsIgnoreCase(language)) {
			language = "javascript";
			scriptSource = JAVASCRIPT;
		} else {
			language = "groovy";
			scriptSource = GROOVYSCRIPT;
		}
		LOGGER.info("Using \"{}\" scripting language...", language);
		script =
			String.format(scriptSource, current().nextInt(10)).intern();
		LOGGER.info("Script:{}", script);
		
		ScriptEngine engine =
			SCRIPT_ENGINE_MANAGER.getEngineByName(language);
		engine.put("#jsr223.groovy.engine.keep.globals", "weak");
		Bindings bindings =
			engine.getBindings(ScriptContext.ENGINE_SCOPE);
		Context context = new Context();
		bindings.put("ctx", context);
		bindings.put("logger", LOGGER);
		bindings.put("taskId", taskId);
		
		Object scriptEvalResult =
			engine.eval(script);
		LOGGER.info("Script eval result: {}.", scriptEvalResult);
		
		try {
			return (Result) ((Invocable) engine).invokeFunction("execute", context);
		} finally {
			bindings.clear();
			engine = null;
		}
	};
	
	@Test
	public void testMultiThreadedScriptExecution() {
		
		int max = 20000;
		final ExecutorService service =
			Executors.newFixedThreadPool(32);
		List<Callable<Result>> tasks =
			new ArrayList<>( max + (int)(0.1*max) );
		IntStream.rangeClosed(1, max).forEach(i -> tasks.add(scriptRunner));
		List<Future<Result>> results;
		try {
			results = service.invokeAll(tasks);
		} catch (InterruptedException ex) {
			LOGGER.warn(
				"{} was thrown while waiting for tasks to complete. Details:",
				String.valueOf(ex), ex
			);
			throw new RuntimeException(ex);
		}
		LOGGER.info("Results list size: {}.", results.size());
		
		LOGGER.info("Shutting down executor service: {}.", service);
		service.shutdown();
		LOGGER.info("{} successfully shut down - awaiting termination.", service);
		while (!service.isShutdown()) {
			try {
				service.awaitTermination(10, TimeUnit.SECONDS);
			} catch (InterruptedException ex) {
				LOGGER.warn(String.valueOf(ex), ex);
			}
		}
		LOGGER.info("{} terminated.", service);
		
		// this code is to prevent test case from terminating so that we can 
		// attach to the JVM process and analyze what's going on.
		synchronized (this) {
			try {
				this.wait();
			} catch (InterruptedException ex) {
				LOGGER.warn(String.valueOf(ex), ex);
			}
		}
		
		LOGGER.info("Test case {} completed.", "testMultithreadedScriptExecution");
		
	}
	
}
