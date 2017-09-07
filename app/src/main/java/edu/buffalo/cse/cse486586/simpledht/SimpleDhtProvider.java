package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Created by omkar on 4/13/17.
 */

public class SimpleDhtProvider extends ContentProvider{
    final static String TAG = SimpleDhtProvider.class.getSimpleName();

    Node self = new Node();
    public int port_numberINT;
    public String port_numberSTRING;
    public String port_number_hash;
    public static String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }
    public int getPortNumber(Context context) {
        TelephonyManager tel = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4); //in the 5500s
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2)); //in the 11000s
        return Integer.parseInt(portStr);
    }

    static final int SERVER_PORT = 10000;

    static final String [] REMOTE_PORT = {"5554","5556","5558","5560","5562"};
    ArrayList <String> aliveNodes = new ArrayList<String>();
    ArrayList <PortHashMapping> aliveNodesHashes = new ArrayList<PortHashMapping>();
    public static String delimiter = "###";
    public int seconds = 3;
    public static boolean already_called = false;

    DatabaseHelper databaseHelper;


    @Override
    public boolean onCreate() {
//        Log.d(TAG, "in oncreate");
        databaseHelper = new DatabaseHelper(getContext());

        port_numberINT = getPortNumber(this.getContext());
        port_numberSTRING = String.valueOf(port_numberINT);

        try {
            port_number_hash = genHash(port_numberSTRING);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        self.setPortNumber(port_numberSTRING);
        self.setPortHashID(port_number_hash);
        self.setAddress();
        self.setSuccessor(self);
        self.setPredecessor(self);

        aliveNodes.add(self.getPortNumber());
        aliveNodesHashes.add(new PortHashMapping(self.getPortNumber(), self.getPortHashID()));

//        Log.d(TAG, "before opening socket");
        try {
            ServerSocket socket = new ServerSocket(SERVER_PORT);

            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, socket);
//            Log.d(TAG, "opening server socket");
        } catch (IOException e) {
            e.printStackTrace();
        }

//        final String message = "connReq"+delimiter+self.getPortNumber()+delimiter+self.getPortHashID();
//        /*
//        attributed to http://stackoverflow.com/questions/2258066/java-run-a-function-after-a-specific-number-of-seconds
//        Not taken Professor's permission for this because this seems like a very general implementation of a timer.
//         */
//
//        new java.util.Timer().schedule(
//                new java.util.TimerTask() {
//                    @Override
//                    public void run() {
//                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message, self.getPortNumber());
//                    }
//                },
//                seconds*1000
//        );
//        /*
//        attribution over
//         */
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if(aliveNodes.size()==1) {
            SQLiteDatabase db = databaseHelper.getReadableDatabase();
            Cursor cursor1 = null;
            if(selection.equals("@")) {
                cursor1 = db.rawQuery("select * from entry", null);
            } else if(selection.equals("*")){
                //query everyone else for their cursors
                cursor1 = db.rawQuery("select * from entry", null);
            } else {
                String query = "select * from entry where key = '"+selection+"'";
                cursor1 = db.rawQuery(query, null);
            }
            return cursor1;
        } else {
            Log.d(TAG, "selection is "+selection+" at port "+self.getPortNumber());
            if(selection.equals("@")) {
                SQLiteDatabase db = databaseHelper.getReadableDatabase();
                Cursor cursor1 = null;
                cursor1 = db.rawQuery("select * from entry",null);
                return cursor1;
            } else if(selection.equals("*")) {
                Log.d("STAR","entered Star");
                SQLiteDatabase db = databaseHelper.getReadableDatabase();
                Cursor cursor1 = null;
                cursor1 = db.rawQuery("select * from entry", null);
                Log.d("STAR","should have read from the database and received a cursor");

                String message = "query*"+delimiter;
                try {
                    Log.d("STAR", "calling clienttask");
                    HashMap<String,String> responHM = new ClientTaskB().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,message, self.getPortNumber()).get();
                    Log.d("STAR", "hashmap size "+responHM.size());

                    Log.d("STAR", "adding contents of hashmap to database StarJoinReader's starjoin table");

                    String[] columns = {"key", "value"};
                    MatrixCursor matrixCursor = new MatrixCursor(columns);
                    for(String key: responHM.keySet()) {
                        String value = responHM.get(key);
                        Log.d("dedupe","k "+key+" value "+value);
                        matrixCursor.addRow(new String[] {key, value});
                    }

                    MergeCursor resultantCursor = new MergeCursor(new Cursor[]{matrixCursor, cursor1});

                    return resultantCursor;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }

                return cursor1;
            } else {
                Log.d(TAG, "selection is not @ or *");

                String keyHash="";
                try {
                    keyHash = genHash(selection);
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }

                String positionNodePort = returnMsgPosition(keyHash);
                Log.d(TAG, "the individual key should be in port "+positionNodePort);
                if(positionNodePort.equals(self.getPortNumber())) {
                    Log.d("individual key lookup","exists in same node "+self.getPortNumber()+"for the key "+selection);
                    SQLiteDatabase db = databaseHelper.getReadableDatabase();


                    String queryString = "select * from entry where key='"+selection+"'";
                    Cursor cursor = db.rawQuery(queryString, null);
                    return cursor;
                } else {
                    String message = "queryKey"+delimiter+positionNodePort+delimiter+selection;
                    String messageValue="";
                    try {
//                        messageValue = new ClientTaskC().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message, self.getPortNumber()).get();
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}),
                                Integer.parseInt(positionNodePort)*2);

                        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                        dos.writeUTF(message);
                        dos.flush();

                        DataInputStream dis = new DataInputStream(socket.getInputStream());
                        messageValue = dis.readUTF();
                        dis.close();
                        dos.close();
                        socket.close();
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    String[] columns = {"key", "value"};
                    MatrixCursor matrixCursor = new MatrixCursor(columns);
                    Object[] row = new Object[matrixCursor.getColumnCount()];

                    Log.d("MATRIX CURSOR","selection: "+selection+"  and value: "+messageValue);
                    row[matrixCursor.getColumnIndex(databaseHelper.COLUMN_NAME_TITLE)] = selection;
                    row[matrixCursor.getColumnIndex(databaseHelper.COLUMN_NAME_SUBTITLE)] = messageValue;
                    matrixCursor.addRow(row);
                    matrixCursor.moveToFirst();

                    return matrixCursor;
                }

            }
        }
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
//        Log.d(TAG, "insert");
        if(already_called == false) {
            final String message = "connReq"+delimiter+self.getPortNumber()+delimiter+self.getPortHashID();
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message, self.getPortNumber());
            Log.d(TAG, "already called");
            already_called = true;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        String portNum = "";
        Collections.sort(aliveNodesHashes);
        for(PortHashMapping phm : aliveNodesHashes) {
            portNum = portNum+" "+phm.getPortNumber();
        }
        Log.d(TAG, "alivenodes  are "+portNum);

        String[] valuesArr = printContentValues(contentValues);
        String key = valuesArr[3];
        String value = valuesArr[1];
        String keyHash = "";
        try {
            keyHash = genHash(key);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        String applicablePort = returnMsgPosition(keyHash);

        if(applicablePort.equals(self.getPortNumber())) {
            SQLiteDatabase sqLiteDatabase = databaseHelper.getWritableDatabase();
            sqLiteDatabase.insertWithOnConflict(databaseHelper.TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
        } else {
            String message = "insert"+delimiter+
                    applicablePort+delimiter+
                    key+delimiter+
                    value;
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message, self.getPortNumber());
        }

        return uri;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        String query = "DROP TABLE IF EXISTS ENTRY";
        if(aliveNodes.size() == 1){
            if(selection.equals("*") || selection.equals("@")) {
                SQLiteDatabase sqLiteDatabase = databaseHelper.getWritableDatabase();
                sqLiteDatabase.execSQL(query);
                sqLiteDatabase.close();
            } else {
                String query2 = "delete from entry where key='"+selection+"'";
                SQLiteDatabase sqLiteDatabase = databaseHelper.getWritableDatabase();
                sqLiteDatabase.execSQL(query2);
                sqLiteDatabase.close();
            }
        } else {
            if(selection.equals("@")) {
                SQLiteDatabase sqLiteDatabase = databaseHelper.getWritableDatabase();
                sqLiteDatabase.execSQL(query);
            } else if (selection.equals("*")) {
                SQLiteDatabase sqLiteDatabase = databaseHelper.getWritableDatabase();
                sqLiteDatabase.execSQL(query);

                for(int i=0; i<aliveNodes.size(); i++) {
                    if(aliveNodes.get(i).equals(self.getPortNumber())) {
                        continue;
                    } else {
                        String msgToSend = "delete"+delimiter+query;
                        try {
                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}),
                                    Integer.parseInt(aliveNodes.get(i))*2);
                            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                            dos.writeUTF(msgToSend);
                            dos.flush();

                            DataInputStream dis = new DataInputStream(socket.getInputStream());
                            String ack = dis.readUTF();
                            if(ack.equals("close")) {
                                dis.close();
                                dos.close();
                                socket.close();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else {
                try {
                    String applicablePort = returnMsgPosition(genHash(selection));
                    String query2 = "delete from entry where key='"+selection+"'";

                    if(aliveNodes.size() == 1) {
                        SQLiteDatabase sqLiteDatabase = databaseHelper.getWritableDatabase();
                        sqLiteDatabase.execSQL(query);
                    } else {
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[] {10,0,2,2}),
                                        Integer.parseInt(applicablePort)*2);
                        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                        String msgToSend = "delete"+delimiter+query2;
                        dos.writeUTF(msgToSend);

                        DataInputStream dis = new DataInputStream(socket.getInputStream());
                        String response = dis.readUTF();
                        if(response.equals("close")) {
                            dos.flush();
                            dos.close();
                            dis.close();
                            socket.close();
                        }
//                        for(int i=0; i<aliveNodes.size(); i++) {
//                            if(aliveNodes.get(i).equals(self.getPortNumber())) {
//                                continue;
//                            } else {
//                                Socket socket = new Socket(InetAddress.getByAddress(new byte[] {10,0,2,2}),
//                                        Integer.parseInt(aliveNodes.get(i))*2);
//                                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
//                                dos.writeUTF("delete"+delimiter+query2);
//                                dos.flush();
//
//                                DataInputStream dis = new DataInputStream(socket.getInputStream());
//                                String response = dis.readUTF();
//                                if(response.equals("close")) {
//                                    dos.close();
//                                    dis.close();
//                                    socket.close();
//                                }
//                            }
//                        }
                    }
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
        return 0;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        @Override
        protected Void doInBackground(ServerSocket... serverSockets) {
            try {
                ServerSocket socket = serverSockets[0];
                while (true) {
                    try {
                        Socket rocket = socket.accept();

                        DataInputStream dis = new DataInputStream(rocket.getInputStream());
                        String response = dis.readUTF();
                        Log.d("SERVERTASK", "Response received: "+response);
                        String[] responArr = response.split(delimiter);

                        if(responArr[0].equals("connReq")) {
                            Log.d("connreq","connection request received");
                            String responderPort = responArr[1];
                            String responderHash = responArr[2];
                            int responderAddressINT = Integer.parseInt(responderPort)*2;

                            if(!aliveNodes.contains(responArr[1])) {
                                aliveNodes.add(responArr[1]);
                                String portHash = genHash(responArr[1]);
                                aliveNodesHashes.add(new PortHashMapping(responArr[1], portHash));
                            }


                            DataOutputStream dos = new DataOutputStream(rocket.getOutputStream());
                            dos.writeUTF("close");
                            dos.flush();
                            dos.close();
                            dis.close();
                            rocket.close();
                        } else if(responArr[0].equals("insert")) {
                            Log.d("SERVERTASK", "insert message received");
                            String key = responArr[1];
                            String value = responArr[2];

                            ContentValues contentValues = new ContentValues();
                            contentValues.put("key",key);
                            contentValues.put("value",value);

                            SQLiteDatabase sqLiteDatabase = databaseHelper.getWritableDatabase();
                            sqLiteDatabase.insertWithOnConflict(databaseHelper.TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);

                            DataOutputStream dos = new DataOutputStream(rocket.getOutputStream());
                            dos.writeUTF("close");
                            dos.flush();
                            dos.close();
                            dis.close();
                            rocket.close();
                        } else if(responArr[0].equals("query*")) {
                            Log.d("SERVERT QUERY*","entered");
                            SQLiteDatabase sqLiteDatabase = databaseHelper.getReadableDatabase();
                            Cursor cursor = sqLiteDatabase.rawQuery("select * from Entry", null);
                            Log.d("SERVERT QUERY*","got readable database");
                            ArrayList<String> resultAL = new ArrayList<String>();
                            //Attribute: http://stackoverflow.com/questions/10723770/whats-the-best-way-to-iterate-an-android-cursor
                            while (cursor.moveToNext()) {
                                String key = cursor.getString(cursor.getColumnIndex(databaseHelper.COLUMN_NAME_TITLE));
                                String value = cursor.getString(cursor.getColumnIndex(databaseHelper.COLUMN_NAME_SUBTITLE));
                                Log.d("SERVERT QUERY* keyval","k "+key);
                                Log.d("SERVERT QUERY*","value "+value);
                                resultAL.add(key+delimiter+value);
                            }
                            //attribute over
                            Log.d("SERVERT QUERY*","al size is "+resultAL.size());
                            String[] result = new String[resultAL.size()];
                            for(int i=0; i<resultAL.size(); i++) {
                                result[i] = resultAL.get(i);
                            }
                            Log.d("SERVERT QUERY* result","result size is "+result.length);

                            ObjectOutputStream oos = new ObjectOutputStream(rocket.getOutputStream());
                            oos.writeObject(result);
                            oos.flush();

                            dis.close();
                            oos.close();
                            rocket.close();
                        } else if(responArr[0].equals("queryKey")) {
                            Log.d("query lookup", "query lookup landed here");
                            String selection = responArr[2];

                            SQLiteDatabase db = databaseHelper.getReadableDatabase();
                            Log.d(TAG, "selection was at query lookup: "+selection);

                            String queryString = "select value from entry where key=?";
                            Cursor c = db.rawQuery(queryString, new String[]{selection}, null);

                            String result = "";
                            DataOutputStream dos = new DataOutputStream(rocket.getOutputStream());
                            if (c.moveToFirst())
                                result = c.getString(c.getColumnIndex("value"));

                            Log.d("return lookup","returning string "+result);

                            Log.d("query return value",result);
                            dos.writeUTF(result);
                            dos.flush();
                            dos.close();
                            dis.close();
                            rocket.close();
                        } else if(responArr[0].equals("delete")) {
                            String query = responArr[1];
                            SQLiteDatabase sqLiteDatabase = databaseHelper.getWritableDatabase();
                            sqLiteDatabase.execSQL(query);

                            DataOutputStream dos = new DataOutputStream(rocket.getOutputStream());
                            dos.writeUTF("close");
                            dos.flush();
                            dos.close();
                            dis.close();
                            rocket.close();
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


            return null;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... strings) {
            String message = strings[0];

            String[] msgArr = message.split(delimiter);
            if(msgArr[0].equals("connReq")) {
                Log.d("connreq","what i am sending is a connection request");
                try {
                    for(int i=0; i<REMOTE_PORT.length; i++) {
                        if(REMOTE_PORT[i].equals(self.getPortNumber())) {
                            continue;
                        }
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(REMOTE_PORT[i])*2);

                        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                        dos.writeUTF(message);
                        dos.flush();

                        DataInputStream dis = new DataInputStream(socket.getInputStream());
                        String ack="";
                        try {
                            ack = dis.readUTF();
//                            Log.d("CLIENTTASK","got ack as"+ack);

                            if(ack.equals("close")) {
//                                Log.d("Client task","connection succesful with "+REMOTE_PORT[i]);
                                if(!aliveNodes.contains(REMOTE_PORT[i])) {
                                    aliveNodes.add(REMOTE_PORT[i]);

                                    try {
                                        aliveNodesHashes.add(new PortHashMapping(REMOTE_PORT[i], genHash(REMOTE_PORT[i])));
                                    } catch (NoSuchAlgorithmException e) {
                                        e.printStackTrace();
                                    }
                                    Collections.sort(aliveNodesHashes);
//                                    Log.d("HASHVALUE", aliveNodesHashes.get(0).getPortHash());
                                }
                                dis.close();
                                dos.close();
                                socket.close();
                            }
                        } catch (EOFException eof) {
                            Log.d("CLIENTTASK", "exception while communicating with "+REMOTE_PORT[i]);
                            eof.printStackTrace();
                            continue;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
                    for(String blah: aliveNodes) {
                        Log.d(TAG, "alivenodes "+blah);
                    }
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if(msgArr[0].equals("insert")) {
                String receiverPort = msgArr[1];
                String msgToSend = "insert"+delimiter+msgArr[2]+delimiter+msgArr[3];

                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(receiverPort)*2);

                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                    dos.writeUTF(msgToSend);

                    DataInputStream dis = new DataInputStream(socket.getInputStream());
                    String ack = dis.readUTF();
                    if(ack.equals("close")) {
                        dos.flush();
                        dos.close();
                        dis.close();
                        socket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            Log.d(TAG, " ");
            return null;
        }
    }

    public String returnMsgPosition (String msgHash) {
        //returns a hash-port mapping to tell you which port to store the message or query it from
        String keyHash = msgHash;
        Collections.sort(aliveNodesHashes);

        PortHashMapping applicablePort = aliveNodesHashes.get(0);

        if(aliveNodesHashes.size() == 1){
            return aliveNodesHashes.get(0).getPortNumber();
        } else {
            for(int i=aliveNodesHashes.size()-1; i>=0; i--) {
                if(keyHash.compareTo(aliveNodesHashes.get(i).getPortHash()) == 1) {
                    continue;
                } else if(keyHash.compareTo(aliveNodesHashes.get(i).getPortHash()) < 1) {
                    applicablePort = aliveNodesHashes.get(i);
                }
            }
        }
        return applicablePort.getPortNumber();
    }

    /*
    START
    http://stackoverflow.com/questions/2390244/how-to-get-the-keys-from-contentvalues

    Taken with permission from the professor
     */
    public String[] printContentValues(ContentValues values)
    {
        Set<Map.Entry<String, Object>> s=values.valueSet();
        Iterator itr = s.iterator();

        ArrayList<String> result_AL = new ArrayList<String>();

//        Log.d("DatabaseSync", "ContentValue Length :: " +values.size());

        while(itr.hasNext())
        {
            Map.Entry me = (Map.Entry)itr.next();
            String key = me.getKey().toString();
            Object value =  me.getValue();

//            Log.d("DatabaseSync", "Key:"+key+", values:"+(String)(value == null?null:value.toString()));

            result_AL.add(key);
            result_AL.add((String)(value == null?null:value.toString()));
        }
        String[] result = result_AL.toArray(new String[result_AL.size()]);

        return result;
    }
    /*
    Taken with permission from the professor

    END
     */

    private class ClientTaskB extends AsyncTask<String, Void, HashMap<String, String>>{
        @Override
        protected HashMap<String, String> doInBackground(String... strings) {
            Log.d("CLIENTTASKB","entered");
            String message = strings[0];

            HashMap <String,String> resultHashMap = new HashMap<String, String>();
            try {
                for(int i=0; i<aliveNodes.size(); i++) {
                    if (aliveNodes.get(i).equals(self.getPortNumber())) {
                        continue;
                    } else {
                        Log.d("CLIENTTASKB","calling node "+aliveNodes.get(i)+" for query");
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(aliveNodes.get(i))*2);
                        Log.d("CLIENTTASKB", "socket connected with port "+aliveNodes.get(i));
                        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                        dos.writeUTF(message);
                        Log.d("CLIENTTASKB","the message is "+message);
                        dos.flush();

                        ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                        Object response = ois.readObject();
                        Log.d("CLIENTTASKB","received an object of type: "+response.getClass());
                        String[] cursorResponse = (String[]) response;
                        ois.close();
                        dos.close();
                        socket.close();

                        for(String line: cursorResponse) {
                            String[] lineSplit = line.split(delimiter);
                            resultHashMap.put(lineSplit[0], lineSplit[1]);
                        }
                    }
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            Log.d("HASHMAP SIZE", "hashmap size is "+resultHashMap.size());
            return resultHashMap;
        }
    }


}