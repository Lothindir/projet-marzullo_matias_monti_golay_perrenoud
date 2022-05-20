package org.hyde;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.Callable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

@Command(name = "build")

class Build implements Callable<Integer> {
   public static final List<String> excluded = List.of("config.yaml", "build");

   @CommandLine.Parameters(index = "0", description = "The path where to build the site.")
   private Path basePath;

   @Override
   public Integer call() {
      File baseFile = new File(String.valueOf(basePath));
      if (!(baseFile.isDirectory()) || !baseFile.exists())
         return 1;
      File buildFile = new File(baseFile.getAbsolutePath() + File.separator + "build");

      // Checks or creates that the build folder exists
      if (!buildFile.exists()) {
         if (!buildFile.mkdir())
            return 1;
      } else {
         if (!buildFile.isDirectory())
            return 1;
      }

      // Starts the recursive building of the folder
      try {
         build(new File(""));
      } catch (IOException e) {
         e.printStackTrace();
         return 1;
      }

      return 0;
   }

   private void build(File file) throws IOException {
      if (excluded.contains(file.getName()))
         return;

      var absPath = new File(basePath + File.separator + file);

      if (absPath.isFile()) {
         metadataTemplating(file);
         buildMD(file);
         if (file.toString().endsWith(".md"))
            checkFileInclusion(file + ".html");
      } else {
         // Liste le contenu du dossier
         for (String subfile : Objects.requireNonNull(absPath.list())) {
            build(new File(String.valueOf(basePath.relativize(Path.of(absPath + File.separator + subfile)))));
         }
      }
   }


   /**
    * Check for file inclusions and charges them
    * @param file path to the HTML file
    * @throws IOException If the reader couldn't read the file
    */
   private void checkFileInclusion(String file) throws IOException {
      if (!file.endsWith(".html")) return;

      BufferedReader mdReader = new BufferedReader(new FileReader(basePath+File.separator+"build"+File.separator+file));
     
      StringBuilder data = new StringBuilder();

      while(mdReader.ready()){
         data.append(mdReader.readLine()).append("\n");
      }
      mdReader.close();
     
      Pattern filePattern = Pattern.compile("\\[\\[\\+ '.+\\.html' ]]");

      Matcher fileMatcher = filePattern.matcher(data.toString());


      while (fileMatcher.find()) {
         String fileName = fileMatcher.group().substring(5, fileMatcher.group().length() - 4);
         System.out.println(fileName);

         BufferedReader fileInclusionReader = new BufferedReader(new FileReader(basePath+File.separator+"template"+File.separator+fileName));
         System.out.println(basePath+File.separator+"template"+File.separator+fileName);
         BufferedWriter mdWriter = new BufferedWriter(new FileWriter(basePath+File.separator+"build"+File.separator+file));
         StringBuilder fileInclusionData = new StringBuilder();

         while(fileInclusionReader.ready()){
            fileInclusionData.append(fileInclusionReader.readLine()).append("\n");
         }

         data = new StringBuilder(data.toString().replaceFirst("\\[\\[\\+ '.+\\.html' ]]", fileInclusionData.toString()));

         mdWriter.write(String.valueOf(data));
         mdWriter.close();
      }
   }


   /**
    * Check for layout template, if present, charges content in there
    * @param data content of the parsed html
    * @throws IOException If the reader couldn't read the file
    */
   private String putContentToLayout(String data) throws IOException {
      File file = new File(basePath+File.separator+"template"+File.separator+"layout.html");
      if(!file.exists())
         return data;
      System.out.println("test");

      BufferedReader fileReader = new BufferedReader(new FileReader(file));
      StringBuilder layoutcontent = new StringBuilder();

      while(fileReader.ready()){
         layoutcontent.append(fileReader.readLine()).append("\n");
      }
      fileReader.close();

      Pattern filePattern = Pattern.compile("\\[\\[ content ]]");

      Matcher fileMatcher = filePattern.matcher(layoutcontent);


      while (fileMatcher.find()) {
         data = String.valueOf(new StringBuilder(layoutcontent.toString().replaceFirst("\\[\\[ content ]]", data)));
      }

      return data;
   }
  
  /*
    * Charges the metadatas requested in the file
    * @param file path to the md file
    * @throws IOException If the reader couldn't read the config file
    */
   private void metadataTemplating(File file) throws IOException {
      if (!file.toString().endsWith("md")) return;

      BufferedReader configReader = new BufferedReader(new FileReader(basePath+File.separator+"config.yaml"));
      BufferedReader mdReader = new BufferedReader(new FileReader(basePath+File.separator+file));
      List<String> configFile = new ArrayList<>();
  
      StringBuilder data = new StringBuilder();

      while(mdReader.ready()){
         data.append(mdReader.readLine()).append("\n");
      }
      mdReader.close();
      while(configReader.ready()){
         configFile.add(configReader.readLine());
      }

      Pattern configPattern = Pattern.compile("\\[\\[ config.\\S+ ]]");
      Pattern pagePattern = Pattern.compile("\\[\\[ page.\\S+ ]]");

      Matcher configMatcher = configPattern.matcher(data.toString());


      while (configMatcher.find()) {
         String configName = configMatcher.group().substring(10, configMatcher.group().length() - 3);
         String configValue = "";

         for(String line : configFile){
            if(line.contains(configName)){
               configValue = line.substring(configName.length()+1);
               break;
            }
         }

         if(!configValue.isEmpty())
            data = new StringBuilder(data.toString().replaceFirst("\\[\\[ config.\\S+ ]]", configValue));
      }

      if(data.toString().startsWith("---")){
         String pageMetadatas = data.substring(0, data.indexOf("..."));
         data = new StringBuilder(data.substring(data.indexOf("...") + 4));;

         Matcher pageMatcher = pagePattern.matcher(data.toString());

         while (pageMatcher.find()) {
            String configName = pageMatcher.group().substring(8, pageMatcher.group().length() - 3);
            String configValue = "";

            if(pageMetadatas.contains(configName)){
               configValue = pageMetadatas.substring(pageMetadatas.indexOf(configName) + configName.length()+1, pageMetadatas.indexOf('\n', pageMetadatas.indexOf(configName)));
            }

            if(!configValue.isEmpty())
               data = new StringBuilder(data.toString().replaceFirst("\\[\\[ page.\\S+ ]]", configValue));
         }
      }

      BufferedWriter mdWriter = new BufferedWriter(new FileWriter(basePath+File.separator+file));

      mdWriter.write(String.valueOf(data));
      mdWriter.close();

   }

   /**
    * Check for file inclusions and charges them
    * @param file path to the HTML file
    * @throws IOException If the reader couldn't read the file
    */
   private void checkFileInclusion(String file) throws IOException {
      if (!file.endsWith(".html")) return;

      BufferedReader mdReader = new BufferedReader(new FileReader(basePath+File.separator+"build"+File.separator+file));
     
      StringBuilder data = new StringBuilder();

      while(mdReader.ready()){
         data.append(mdReader.readLine()).append("\n");
      }
      mdReader.close();
     
      Pattern filePattern = Pattern.compile("\\[\\[\\+ '.+\\.html' ]]");

      Matcher fileMatcher = filePattern.matcher(data.toString());


      while (fileMatcher.find()) {
         String fileName = fileMatcher.group().substring(5, fileMatcher.group().length() - 4);
         System.out.println(fileName);

         BufferedReader fileInclusionReader = new BufferedReader(new FileReader(basePath+File.separator+"template"+File.separator+fileName));
         System.out.println(basePath+File.separator+"template"+File.separator+fileName);
         BufferedWriter mdWriter = new BufferedWriter(new FileWriter(basePath+File.separator+"build"+File.separator+file));
         StringBuilder fileInclusionData = new StringBuilder();

         while(fileInclusionReader.ready()){
            fileInclusionData.append(fileInclusionReader.readLine()).append("\n");
         }

         data = new StringBuilder(data.toString().replaceFirst("\\[\\[\\+ '.+\\.html' ]]", fileInclusionData.toString()));

         mdWriter.write(String.valueOf(data));
         mdWriter.close();
      }
   }


   /**
    * Check for layout template, if present, charges content in there
    * @param data content of the parsed html
    * @throws IOException If the reader couldn't read the file
    */
   private String putContentToLayout(String data) throws IOException {
      File file = new File(basePath+File.separator+"template"+File.separator+"layout.html");
      if(!file.exists())
         return data;
      System.out.println("test");

      BufferedReader fileReader = new BufferedReader(new FileReader(file));
      StringBuilder layoutcontent = new StringBuilder();

      while(fileReader.ready()){
         layoutcontent.append(fileReader.readLine()).append("\n");
      }
      fileReader.close();

      Pattern filePattern = Pattern.compile("\\[\\[ content ]]");

      Matcher fileMatcher = filePattern.matcher(layoutcontent);


      while (fileMatcher.find()) {
         data = String.valueOf(new StringBuilder(layoutcontent.toString().replaceFirst("\\[\\[ content ]]", data)));
      }

      return data;
   }
  
  /*
    * Charges the metadatas requested in the file
    * 
    * @param file path to the md file
    * @throws IOException If the reader couldn't read the config file
    */
   private void metadataTemplating(File file) throws IOException {
      if (!file.toString().endsWith("md"))
         return;

      try (
            BufferedReader configReader = new BufferedReader(new FileReader(basePath + File.separator + "config.yaml"));
            BufferedReader mdReader = new BufferedReader(new FileReader(basePath + File.separator + file));
            BufferedWriter mdWriter = new BufferedWriter(new FileWriter(basePath + File.separator + file))) {

         List<String> configFile = new ArrayList<>();
         StringBuilder data = new StringBuilder();

         while (mdReader.ready()) {
            data.append(mdReader.readLine()).append("\n");
         }

         while (configReader.ready()) {
            configFile.add(configReader.readLine());
         }

         Pattern configPattern = Pattern.compile("\\[\\[ config.\\S+ ]]");
         Pattern pagePattern = Pattern.compile("\\[\\[ page.\\S+ ]]");

         Matcher configMatcher = configPattern.matcher(data.toString());

         while (configMatcher.find()) {
            String configName = configMatcher.group().substring(10, configMatcher.group().length() - 3);
            String configValue = "";

            for (String line : configFile) {
               if (line.contains(configName)) {
                  configValue = line.substring(configName.length() + 1);
                  break;
               }
            }

            if (!configValue.isEmpty())
               data = new StringBuilder(data.toString().replaceFirst("\\[\\[ config.\\S+ ]]", configValue));
         }

         if (data.toString().startsWith("---")) {
            String pageMetadatas = data.substring(0, data.indexOf("..."));
            data = new StringBuilder(data.substring(data.indexOf("...") + 4));
            ;

            Matcher pageMatcher = pagePattern.matcher(data.toString());

            while (pageMatcher.find()) {
               String configName = pageMatcher.group().substring(8, pageMatcher.group().length() - 3);
               String configValue = "";

               if (pageMetadatas.contains(configName)) {
                  configValue = pageMetadatas.substring(pageMetadatas.indexOf(configName) + configName.length() + 1,
                        pageMetadatas.indexOf('\n', pageMetadatas.indexOf(configName)));
               }

               if (!configValue.isEmpty())
                  data = new StringBuilder(data.toString().replaceFirst("\\[\\[ page.\\S+ ]]", configValue));
            }
         }

         mdWriter.write(String.valueOf(data));
      } catch (Exception e) {
         System.err.println("An error occured while converting to HTML");
         System.err.println(e.getMessage());
      }
   }

   /**
    * Builds the HTML code of a page from a .md
    * 
    * @param file a .md, relative path from source folder
    * @throws IOException If the file cannot be opened
    */
   private void buildMD(File file) throws IOException {
      // Checking that the given file is a md !
      if (!file.toString().endsWith("md"))
         return;

      // Absolute path to the file, with given basePath in cmd
      File absFile = new File(basePath + File.separator + file);

      // Checking that the file isn't a directory !
      if (absFile.isDirectory() || !absFile.exists())
         throw new IllegalArgumentException("'" + absFile + "' isn't a valid file !");

      // Path to HTML file
      File buildFile = new File(basePath + File.separator + "build" + File.separator + file + ".html");

      // Checks that the corresponding folder exists in the build folder
      if (!buildFile.getParentFile().exists() && !buildFile.getParentFile().mkdirs()) {
         throw new RuntimeException("Can't create folder '" + buildFile.getParentFile() + "' !");
      }

      // Converts MD to HTML
      BufferedReader reader = new BufferedReader(new FileReader(absFile));
      Parser parser = Parser.builder().build();
      Node document = parser.parseReader(reader);
      HtmlRenderer renderer = HtmlRenderer.builder().build();
      var data = renderer.render(document);

      data = putContentToLayout(data);

      // Dumps the datas to the HTML file
      try (
            FileOutputStream fos = new FileOutputStream(buildFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos)) {
         byte[] bytes = data.getBytes();
         bos.write(bytes);
      } catch (IOException e) {
         e.printStackTrace();
      }

   }

   public static void main(String... args) {
      int exitCode = new CommandLine(new Build()).execute(args);
      System.exit(exitCode);
   }
}