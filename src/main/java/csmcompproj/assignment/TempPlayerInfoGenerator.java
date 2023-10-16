package csmcompproj.assignment;

import java.util.*;

public class TempPlayerInfoGenerator {

  Map<String, Map<String, String>> playerInfo = new HashMap<>();

  public TempPlayerInfoGenerator(String id, String IP, int port) {
    Map<String, String> connectionDetails = new HashMap<>();
    connectionDetails.put("IP", IP);
    connectionDetails.put("PORT", Integer.toString(port));
    this.playerInfo.put(id, connectionDetails);
  }

  public Map<String, Map<String, String>> getPlayerDetails() {
    return this.playerInfo;
  }

}
