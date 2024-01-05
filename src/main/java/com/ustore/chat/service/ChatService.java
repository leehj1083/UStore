package com.ustore.chat.service;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ustore.chat.dao.ChatDao;
import com.ustore.chat.dto.ChatDto;
import com.ustore.chat.dto.ChatRoomDto;
import com.ustore.chat.dto.Participant;
import com.ustore.config.WebSocketConfig;


@Service
public class ChatService {
	Logger logger = LoggerFactory.getLogger(getClass());
	@Autowired
	private ChatDao chatDao;
	@Autowired 
	WebSocketConfig config;
	@Transactional
	public ChatDto saveChat(ChatDto chat) {
		// 발신 히스토리 쌓기
		Timestamp timestamp = new Timestamp(System.currentTimeMillis());
		chat.setSendDate(timestamp);
		int row = chatDao.insertSendMsg(chat);
		
		List<String> receiveMembers = chatDao.selectReceiveMember(chat.getRoomNum(), chat.getSender());
		for(String member : receiveMembers) {
			chat.setReceiver(member);
			if(chat.getSender().equals("system")) {
				chat.setRead("Y");
			}else {
				chat.setRead("N");
			}
			logger.info("room_num : "+chat.getRoomNum());
			row += chatDao.insertReceivedMsg(chat);	
		}
		return chat;
	}
	
	@Transactional
	public void makeRoom(List<Participant> list, String emp_idx) {
		//예외 처리 하기
		String roomemp_idx="";
		
		logger.info(roomemp_idx);
		ChatRoomDto chatRoomDto = new ChatRoomDto();
		chatRoomDto.setChatRoomName(roomemp_idx);
		chatRoomDto.setRegBy(emp_idx);
		chatRoomDto.setIsIndividual(list.size()>1 ? "N":"Y");
		//만든 후 채팅방 생성
		chatDao.insertChatRoom(chatRoomDto);
		int roomIdx = chatRoomDto.getChatRoomIdx();
		for(Participant chatParticipants : list) {
			chatDao.insertChatParticipants(roomIdx, chatParticipants.getEmpIdx());			
		} 
		chatDao.insertChatParticipants(roomIdx, emp_idx);	
		
		ChatDto wellcomMsg = new ChatDto();
		wellcomMsg.setRoomNum(Integer.toString(roomIdx));
		wellcomMsg.setSender("system");
		wellcomMsg.setData("");
		saveChat(wellcomMsg);
		
	}
	
	public List<ChatRoomDto> getChatRoomList(String emp_idx) {
		List<ChatRoomDto> list = chatDao.selectChatRoomList(emp_idx);
		List<Participant> participant = null;
		for(ChatRoomDto dto : list) {
			participant = chatDao.selectParticipants(dto.getChatRoomIdx());
			if(dto.getIsIndividual().equals("Y")) {
				for(int i=0; i<participant.size(); i++) {
					if(!participant.get(i).getEmpIdx().equals(emp_idx)) {
						dto.setChatRoomName(participant.get(i).getEmpInfo());
					}
				}
			}else {
				String roomName="";
				for(int i=0; i<3; i++) {
					roomName+=participant.get(i).getEmpInfo();
					if(i != 2) {
						roomName+=",";
					}
				}
				if(participant.size()>3) {
					roomName+=" 외 "+(participant.size()-3)+"명";
				}
				dto.setChatRoomName(roomName);
			}
		}
		for(ChatRoomDto dto : list) {
			int i = dto.getMaxSentDate().compareTo(dto.getMaxReceivedDate());
			long maxSentDate = dto.getMaxSentDate().getTime();
			long maxReceivedDate =  dto.getMaxReceivedDate().getTime();
			if(i>0) {
				dto.setLastMsgTime(maxSentDate);
			}else if(i<0) {
				logger.info("lastMsgTime"+dto.getMaxReceivedDate().getTime());
				dto.setLastMsgTime(maxReceivedDate);
			}else {
				dto.setLastMsgTime(maxSentDate);
			}
		}
		Collections.sort(list);
		config.printSubscription();
		return list;
	}

	
	public List<ChatDto> getChatData(int roomNum, String emp_idx) {
		chatDao.updateToRead(roomNum, emp_idx);
		List<ChatDto> chatList = chatDao.selectChatHistory(roomNum,emp_idx);
		return chatList;
	}
	
	public List<Participant> getParticipantLists(int roomNum) {
		List<Participant> participants = chatDao.selectParticipants(roomNum);
		return participants;
	}

	public ChatDto quitRoom(int roomNum, String name) {
		// -> 나가면 system 메시지 보내기
		String userInfo = chatDao.selectUserInfo(name);
		ChatDto leaveMsg = new ChatDto();
		leaveMsg.setRoomNum(Integer.toString(roomNum));
		leaveMsg.setSender("system");
		leaveMsg.setData(userInfo+"님이 채팅방을 나가셨습니다");
		saveChat(leaveMsg);
		chatDao.deleteParticipants(roomNum,name);
		return leaveMsg;
	}

	public void setRead(int roomNum, int chatIdx, String name) {
		chatDao.setRead(roomNum, chatIdx, name);
	}

}
