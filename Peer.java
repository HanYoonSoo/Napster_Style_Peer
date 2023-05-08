import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class Peer {

    static String targetNum;	// Peer의 숫자 야구 게임 숫자
    static int userNum;	// Peer 식별 번호
    static String nickName;	// Peer의 닉네임
    static boolean threadControl;

    /*
        각종 매핑 정보를 저장하기 위한 변수들
     */
    static Map<String, Integer> ipMap;  // IP주소와 식별번호 매핑을 위한 Map
    static Map<Integer, BufferedReader> brMap; // 식별번호와 유저별 BufferedReader 매핑을 위한 Map
    static Map<Integer, PrintWriter> pwMap; // 식별번호와 유저별 PrintWriter 매핑을 위한 Map
    static Map<Integer, Socket> socketMap;  // 식별번호와 유저별 Socket 매핑을 위한 Map
    static Map<String, String> nickNameMap; // 닉네임과 IP 매핑을 위한 Map
    static Map<String, String> userGuessMap; // 유저이름과 추측한 숫자 매핑을 위한 Map

    static int peerServerPort = 9001;   // Peer의 서버 소켓 포트

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = null;   // Peer 개인 서버 소켓
        Socket socket = null;   // 서버와 소통하기 위한 Socket
        Socket peerSocket = null; // 다른 Peer와 소통하기 위한 Socket
        System.out.print("사용하실 닉네임을 적어주세요: ");  // 사용자 식별을 위한 닉네임 입력
        BufferedReader nickBr = new BufferedReader(new InputStreamReader(System.in));

        nickName = nickBr.readLine();

        try {
            System.out.println("서버에 접속 요청");
            socket = new Socket("localhost", 9000); // 중앙 서버 접속
            serverSocket = new ServerSocket(peerServerPort);  // Peer 개인 서버 소켓 열기

            PeerServerThread peerServerThread = new PeerServerThread(serverSocket); // 다른 Peer들과의 접속을 위한 서버 스레드 생성
            peerServerThread.start();

            System.out.println("서버에 접속 됨");

            InputStream is = socket.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            OutputStream os = socket.getOutputStream(); //송신 --> write();
            PrintWriter pw = new PrintWriter(os);

            /*
                각 Map객체 할당
             */
            ipMap = new HashMap<>();
            brMap = new HashMap<>();
            pwMap = new HashMap<>();
            socketMap = new HashMap<>();
            nickNameMap = new HashMap<>();
            userGuessMap = new HashMap<>();

            userNum = 1;    // Peer 식별 번호
            threadControl = false;  // logoff했을 경우 운영중인 스레드 종료를 위한 변수

            BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

            pw.write(nickName + " " + peerServerPort + "\r");  // 중앙 서버에 닉네임 및 포트 번호 전송
            pw.flush();

            String[] command;

            System.out.print("숫자 야구 게임을 위한 세자리 숫자를 입력해주세요: ");  // 숫자 야구를 위한 숫자 설정

            // 입력한 수가 숫자가 맞는지 검사
            while(!(targetNum = stdin.readLine()).matches("[-+]?\\d*\\.?\\d+")){
                System.out.print("잘못된 숫자입니다. 올바른 세자리 숫자를 입력해주세요: ");
            }

            System.out.println();
            System.out.println("관련 명령어를 살펴보려면 help를 입력해주세요.");

            InputStream useris = null;
            BufferedReader userbr = null;
            OutputStream useros = null; //송신 --> write();
            PrintWriter userpw = null;

            /*
                logoff전까지 무한 반복하며 유저의 명령어를 읽어 들임
             */
            while (true) {
                System.out.print("명령어 입력: ");
                command = stdin.readLine().split(" ");

                int ch;
                switch (command[0]) {
                    /*
                        help, online_users 명령어의 경우 중앙 서버에서 처리되기에 해당 내용을 중앙 서버로 보냄
                        그 후, 서버로부터의 응답을 받음.
                     */
                    case "help", "online_users":
                        pw.print(command[0] + "\r");
                        pw.flush();

                        while (true) {
                            ch = br.read();
                            if (ch < 0 || ch == '\r')
                                break;
                            System.out.print((char) ch);    // 서버의 응답을 출력.
                        }
                        System.out.println();
                        break;

                    /*
                        다른 Peer와 연결을 위한 명령어
                     */
                    case "connect":
                        try {
                            System.out.println("유저 접속 요청");
                            peerSocket = new Socket(command[1], Integer.parseInt(command[2]));  // 사용자의 입력을 토대로 다른 Peer와 연결

                            /*
                                연결된 Peer와 소통할 수 있는 BufferedReader와 PrintWriter 생성
                             */
                            useris = peerSocket.getInputStream();
                            userbr = new BufferedReader(new InputStreamReader(useris));
                            useros = peerSocket.getOutputStream(); //송신 --> write();
                            userpw = new PrintWriter(useros);

                            /*
                                연결된 Peer에게 개인 닉네임 전송
                             */
                            userpw.write(nickName + "\r");
                            userpw.flush();

                            /*
                                연결된 Peer의 닉네임을 전송받기 위해 대기
                             */
                            String name = "";
                            boolean compare = false;

                            while (!compare) {
                                while (true) {
                                    ch = userbr.read();
                                    if (ch < 0 || ch == '\n' || ch == '\r')
                                        break;
                                    name += (char) ch;
                                }

                                /*
                                    연결된 Peer의 닉네임 전송받기 성공
                                 */
                                if (name.length() > 0) {
                                    compare = true;
                                }
                            }

                            if(name.equals("error")){
                                System.out.println("중복 IP 로그인입니다.");
                            }
                            else{
                                System.out.println(name + "유저와 접속 됨");
                            }

                            /*
                                연결된 Peer와 관련된 모든 정보를 각 Map 객체에 저장
                             */
                            ipMap.put(peerSocket.getInetAddress().getHostAddress(), userNum);
                            brMap.put(userNum, userbr);
                            pwMap.put(userNum, userpw);
                            socketMap.put(userNum, peerSocket);
                            nickNameMap.put(name, peerSocket.getInetAddress().getHostAddress());

                            userNum++;

                            /*
                                연결된 Peer와 숫자 야구 게임을 진행하며 오고가는 응답을 위한 스레드 생성 및 실행
                                연결된 Peer의 BufferedReader와 PrintWriter을 사용한 생성자 이용
                             */
                            PeerGuessThread peerGuessThread = new PeerGuessThread(peerSocket, userbr, userpw, name);
                            peerGuessThread.start();

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;

                    /*
                        Peer간의 연결을 끊기 위한 명렁어
                        관련된 Map에서 연결을 끊기 위한 Peer의 정보를 삭제
                     */
                    case "disconnect":
                        if (nickNameMap.containsKey(command[1])) {
                            System.out.println(command[1] + "유저와의 연결을 종료합니다.");
                            String ip = nickNameMap.get(command[1]);
                            Integer mapIdx = ipMap.get(ip);

                            PrintWriter disconnectPw = pwMap.get(mapIdx);
                            disconnectPw.write("disconnect\r"); // 연결된 Peer에게 disconnect 신호 보내기
                            disconnectPw.flush();

                            /*
                                관련된 정보 삭제
                             */
                            ipMap.remove(ip);
                            brMap.remove(mapIdx);
                            pwMap.remove(mapIdx);
                            socketMap.remove(mapIdx);
                            nickNameMap.remove(command[1]);
                            userGuessMap.remove(command[1]);
                        } else {
                            System.out.println("잘못된 유저입니다.");
                        }
                        break;

                    /*
                        숫자를 추측하기 위한 명령어
                        추측한 내용을 PrintWriter를 사용하여 전달
                     */
                    case "guess":
                        if (nickNameMap.containsKey(command[1])) {
                            Integer mapIdx = ipMap.get(nickNameMap.get(command[1]));
                            PrintWriter guessPw = pwMap.get(mapIdx);

                            guessPw.print(command[2] + "\r");
                            guessPw.flush();

                        } else {
                            System.out.println("잘못된 유저입니다.");
                        }
                        break;


                    /*
                        다른 Peer에서 추측한 나의 숫자에 대한 대답을 위한 명령어
                        check() 메소드를 사용하여 Strike과 Ball에 관한 정보 생성
                     */
                    case "answer":
                        if (nickNameMap.containsKey(command[1])) {
                            Integer mapIdx = ipMap.get(nickNameMap.get(command[1]));
                            String number = userGuessMap.get(command[1]);
                            int[] result = check(number);
                            PrintWriter answerPw = pwMap.get(mapIdx);

                            if (result[0] == 3) {
                                answerPw.write("숫자를 맞춰 승리하셨습니다!\r");
                            } else if (result[0] == 0 && result[1] == 0) {
                                answerPw.write("아웃입니다!\r");
                            } else {
                                answerPw.write(result[0] + " Strikes, " + result[1] + " Balls.\r");
                            }

                            answerPw.flush();
                        } else {
                            System.out.println("잘못된 유저입니다.");
                        }
                        break;

                    /*
                        서버에서 로그오프 하기 위한 명령어
                        관련된 객체를 close하고 Thread 종료 및 프로그램 종료
                     */
                    case "logoff":

                        /*
                            연결되어 있는 Peer와 연결을 끊음
                         */
                        System.out.println("Peer간의 연결을 끊습니다.");
                        for(String key : ipMap.keySet()){
                            Integer mapIdx = ipMap.get(key);
                            PrintWriter temp_pw = pwMap.get(mapIdx);

                            temp_pw.write("disconnect\r");
                            temp_pw.flush();
                        }
                        System.out.println("서버로부터 로그오프 하겠습니다.");
                        pw.print(command[0] + "\r");
                        pw.flush();

                        socket.close();
                        is.close();
                        br.close();
                        os.close();
                        pw.close();

                        threadControl = true;

                        peerServerThread.interrupt();

                        System.out.println("프로그램을 종료하겠습니다.");
                        System.exit(0);
                        break;
                }
            }
        } catch (
                IOException e) {
            e.printStackTrace();
        }
    }

    /*
        전달받은 인자를 통해 Strike과 Ball의 정보를 리턴하기 위한 메소드
        int[] 형태로 리턴
     */
    public static int[] check(String number) {
        int strikes = 0;
        int balls = 0;

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (targetNum.charAt(i) == number.charAt(j)) {
                    if (i == j)
                        strikes++;
                    else
                        balls++;
                }
            }
        }

        return new int[]{strikes, balls};
    }

}

/*
    다른 Peer와의 연결을 위해 항상 실행중인 스레드 클래스
    서버 소켓을 인자로 받아 서버 소켓에 연결이 올 때 마다
    각 유저의 BufferedReader, PrintWriter, Socket, 닉네임 정보를 관련된 Map에 저장
 */
class PeerServerThread extends Thread {
    ServerSocket serverSocket;

    InputStream useris = null;
    BufferedReader userbr = null;
    OutputStream useros = null; //송신 --> write();
    PrintWriter userpw = null;

    public PeerServerThread() {
    }

    public PeerServerThread(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    /*
        스레드가 start되었을 때 실행되는 메소드
     */
    public void run() {
        while (true) {
            Socket peerReceiveSocket = null;
            try {
                peerReceiveSocket = serverSocket.accept();  // 서버 소켓으로 연결이 올 때까지 무한 대기
                if (peerReceiveSocket != null) {
                    if (!Peer.ipMap.containsKey(peerReceiveSocket.getInetAddress().getHostAddress())) { // ipMap에 저장되어 있지 않은 IP인 경우
                        Peer.ipMap.put(peerReceiveSocket.getInetAddress().getHostAddress(), Peer.userNum); // 해당 내용 ipMap에 저장

                        /*
                            서버 소켓으로부터 얻은 소켓과 관련된 BufferedReader, PrintWriter 추출
                         */
                        useris = peerReceiveSocket.getInputStream();
                        userbr = new BufferedReader(new InputStreamReader(useris));
                        useros = peerReceiveSocket.getOutputStream(); //송신 --> write();
                        userpw = new PrintWriter(useros);

                        /*
                            연결된 Peer에게 서버 Peer의 닉네임 전달
                         */
                        userpw.write(Peer.nickName + "\r");
                        userpw.flush();

                        Thread.sleep(1000);

                        /*
                            연결된 Peer의 닉네임을 전달받기 위한 반복문
                         */
                        String name = "";
                        int ch;
                        while (true) {
                            ch = userbr.read();
                            if (ch < 0 || ch == '\n' || ch == '\r')
                                break;
                            name += (char) ch;
                        }

                        System.out.println("\n" + name + "님과 연결되었습니다.");
                        System.out.print("명령어 입력: ");

                        /*
                            새롭게 연결된 Peer의 정보를 관련된 Map에 저장
                         */
                        Peer.brMap.put(Peer.userNum, userbr);
                        Peer.pwMap.put(Peer.userNum, userpw);
                        Peer.socketMap.put(Peer.userNum, peerReceiveSocket);
                        Peer.nickNameMap.put(name, peerReceiveSocket.getInetAddress().getHostAddress());

                        Peer.userNum++; // 다른 Peer를 구분하기 위한 식별 숫자 증가

                        /*
                            연결된 Peer와 숫자 야구 게임을 진행하며 오고가는 응답을 위한 스레드 생성 및 실행
                            연결된 Peer의 BufferedReader와 PrintWriter을 사용한 생성자 이용
                         */
                        PeerGuessThread peerGuessThread = new PeerGuessThread(peerReceiveSocket, userbr, userpw, name);
                        peerGuessThread.start();

                    }
                    else{ // 이미 해당 IP와 연결되어 있는 경우
                        OutputStream os = peerReceiveSocket.getOutputStream();
                        PrintWriter pw = new PrintWriter(os);
                        pw.write("error\r");
                        pw.flush();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }
    }
}

/*
    Peer간의 응답을 출력하기 위한 Thread 클래스
 */
class PeerGuessThread extends Thread {
    Socket socket;

    BufferedReader br = null;
    PrintWriter pw = null;

    String userName;

    public PeerGuessThread() {
    }

    /*
        추측을 위해 관련된 Socket, BufferedReader, PrintWriter를 인자로 받고 유저 식별을 위한 닉네임을 받음
     */
    public PeerGuessThread(Socket socket, BufferedReader br, PrintWriter pw, String userName) {
        this.socket = socket;
        this.br = br;
        this.pw = pw;
        this.userName = userName;
    }

    public void run() {
        int ch;

        try {
            while (!Peer.threadControl) {   // 로그오프전까지 무한반복

                    /*
                        연결된 Peer의 응답을 읽어들이는 반복문
                     */
                String str = "";
                while (true) {
                    ch = br.read();
                    if (ch < 0 || ch == '\n' || ch == '\r')
                        break;
                    str += (char) ch;
                }

                if (str.length() > 0) {

                    // 숫자를 전달받은 경우
                    if(str.length() == 3){
                        Peer.userGuessMap.put(userName, str);
                        System.out.println("\n" + userName + "님이 숫자를 " + str + "로 예측하셨습니다.");
                        System.out.println("answer 명령어로 답변해주세요!");
                        System.out.print("명령어 입력: ");
                    }
                    else if(str.equals("disconnect")){
                        System.out.println("\n" + userName + "유저와의 연결이 종료되었습니다.");
                        System.out.print("명령어 입력: ");
                        String ip = Peer.nickNameMap.get(userName);
                        Integer mapIdx = Peer.ipMap.get(ip);

                        Peer.ipMap.remove(ip);
                        Peer.brMap.remove(mapIdx);
                        Peer.pwMap.remove(mapIdx);
                        Peer.socketMap.remove(mapIdx);
                        Peer.nickNameMap.remove(userName);
                        Peer.userGuessMap.remove(userName);
                    }
                    else if(str.equals("error")){   // 중복 IP일 때
                        System.out.println("\n 중복된 IP 접근입니다.");
                        System.out.print("명령어 입력: ");
                    }
                    else{   // Strike와 Ball의 정보를 전달받은 경우
                        System.out.println("\n" + userName + "유저의 응답은 다음과 같습니다: " + str);
                        System.out.print("명령어 입력: ");
                    }
                }
            }

            // 스레드 종료
            currentThread().interrupt();

        } catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }


}
