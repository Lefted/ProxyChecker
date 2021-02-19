package me.lefted.proxychecker;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;

public class Checker {

    public static final int NUM_THREADS_MAX = 200;
    public static Checker instance;

    public static volatile int left = 0;
    public static volatile int countWorking, countFailing = 0;
    public static volatile List<String> workingCombos = new ArrayList<>();

    public static void main(String[] args) throws IOException {
	instance = new Checker();
	final List<NameValuePair> nvps = instance.getIpPortCombo("src/main/java/me/lefted/proxychecker/input.txt");
	left = nvps.size();

	final ThreadGroup parentThreadGroup = new ThreadGroup("parent thread group");

	for (int i = 0; i < nvps.size(); i++) {

	    while (parentThreadGroup.activeCount() >= NUM_THREADS_MAX) {
	    }

	    final NameValuePair nvp = nvps.get(i);
	    new Thread(parentThreadGroup, () -> {
		try {
		    final int port = Integer.parseInt(nvp.getValue());
		    boolean working = instance.check(nvp.getName(), port);

		    if (working) {
			workingCombos.add(String.format("%s:%s", nvp.getName(), port));
		    }
		    System.out.println(String.format("%-10s   %-21s   working %-10s   failing %-10s  active threads %-10s left %-10s", working ? "working"
			: "failing", String.format("%s:%s", nvp.getName(), port), working ? ++countWorking : countWorking, working ? countFailing
			    : ++countFailing, parentThreadGroup.activeCount(), --left));

		} catch (NumberFormatException e) {
		    left--;
		}
	    }).start();
	}

	while (parentThreadGroup.activeCount() > 1) {
	}

	instance.writeOutput("src/main/java/me/lefted/proxychecker/working.txt");
    }

    private void writeOutput(String path) {
	final File outputFile = new File(path);
	if (!outputFile.exists()) {
	    outputFile.getParentFile().mkdirs();
	    try {
		outputFile.createNewFile();
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}

	try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {

	    for (String line : workingCombos)
		writer.append(line).append("\n");
	} catch (IOException e) {
	    e.printStackTrace();
	}

    }

    private List<NameValuePair> getIpPortCombo(String path) throws IOException {
	final List<NameValuePair> nvps = new ArrayList<>();
	final List<String> lines = Files.readAllLines(Paths.get(path));

	for (String line : lines) {
	    final String[] split = line.split(":");
	    nvps.add(new BasicNameValuePair(split[0], split[1]));
	}

	return nvps;
    }

    private boolean check(String ip, int port) {
	boolean working = false;

	try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {
	    final HttpHost target = new HttpHost("https", "httpbin.org", 443);
	    final HttpHost proxy = new HttpHost("http", ip, port);

	    final RequestConfig config = RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(30)).setConnectionRequestTimeout(Timeout.ofSeconds(30))
		.setProxy(proxy).build();
	    final HttpGet request = new HttpGet("/get");
	    request.setConfig(config);

	    try (final CloseableHttpResponse response = httpclient.execute(target, request)) {
		if (response.getCode() == 200) {
		    working = true;
		}
	    }
	} catch (IOException e) {
	    working = false;
	}

	return working;
    }

}