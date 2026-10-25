/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.scripting.engine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import org.l2jmobius.gameserver.model.script.Quest;

/** Compile/type-load smoke with production script file manager and loader; no quest registration or Player flow. */
public final class QuestRewards090CompileCheck
{
	public static void main(String[] args) throws Exception
	{
		final Path module = Path.of(args[0]);
		final String[] names = { "Q00663_SeductiveWhispers", "Q00384_WarehouseKeepersPastime", "Q00386_StolenDignity", "Q00662_AGameOfCards" };
		final List<String> sources = Arrays.stream(names).map(name -> module.resolve("dist/game/data/scripts/quests/" + name + "/" + name + ".java").toString()).toList();
		final var compiler = ToolProvider.getSystemJavaCompiler();
		final var diagnostics = new DiagnosticCollector<JavaFileObject>();
		try (var files = new ScriptFileManager(compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)))
		{
			final List<String> options = List.of("-source", "1.8", "-target", "1.8", "-sourcepath", module.resolve("dist/game/data/scripts").toString(), "-g:source,lines,vars");
			if (!compiler.getTask(null, files, diagnostics, options, null, files.getJavaFileObjectsFromStrings(sources)).call())
			{
				throw new AssertionError("Casino script compilation failed: " + diagnostics.getDiagnostics());
			}
			final var loader = new ScriptClassLoader(ClassLoader.getSystemClassLoader());
			loader.addCompiledClasses(files.getCompiledClasses());
			for (String name : names)
			{
				final Class<?> type = loader.loadClass("quests." + name + "." + name);
				if (type.getSuperclass() != Quest.class || type.getClassLoader() != loader)
				{
					throw new AssertionError("Expected the freshly compiled native Quest subclass: " + name);
				}
				System.out.println("PASS quest090.compile-type-load " + name + "; initialized=false; native-flow=false");
			}
		}
	}
}
