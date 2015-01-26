package com.jakduk.service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

import com.jakduk.authentication.common.CommonPrincipal;
import com.jakduk.common.CommonConst;
import com.jakduk.dao.JakdukDAO;
import com.jakduk.model.db.BoardCategory;
import com.jakduk.model.db.BoardFree;
import com.jakduk.model.db.BoardFreeComment;
import com.jakduk.model.db.Gallery;
import com.jakduk.model.embedded.BoardHistory;
import com.jakduk.model.embedded.BoardImage;
import com.jakduk.model.embedded.BoardItem;
import com.jakduk.model.embedded.BoardStatus;
import com.jakduk.model.embedded.BoardUser;
import com.jakduk.model.embedded.BoardWriter;
import com.jakduk.model.simple.BoardFreeOnComment;
import com.jakduk.model.simple.BoardFreeOnList;
import com.jakduk.model.web.BoardFreeWrite;
import com.jakduk.model.web.BoardListInfo;
import com.jakduk.model.web.BoardPageInfo;
import com.jakduk.repository.BoardCategoryRepository;
import com.jakduk.repository.BoardFreeCommentRepository;
import com.jakduk.repository.BoardFreeOnListRepository;
import com.jakduk.repository.BoardFreeRepository;
import com.jakduk.repository.GalleryRepository;
import com.jakduk.repository.SequenceRepository;

@Service
public class BoardFreeService {
	
	@Autowired
	private BoardFreeRepository boardFreeRepository;
	
	@Autowired
	private BoardFreeOnListRepository boardFreeOnListRepository;
	
	@Autowired
	private SequenceRepository boardSequenceRepository;
	
	@Autowired
	private BoardCategoryRepository boardCategoryRepository;	
	
	@Autowired
	private BoardFreeCommentRepository boardFreeCommentRepository;
	
	@Autowired
	private GalleryRepository galleryRepository;
	
	@Autowired
	private JakdukDAO boardFreeDAO;
	
	@Autowired
	private CommonService commonService;
	
	@Autowired
	private UserService userService;
	
	private Logger logger = Logger.getLogger(this.getClass());

	/**
	 * 자유게시판 글쓰기 페이지
	 * @param model
	 * @return
	 */
	public Model getWrite(Model model) {
		
		List<BoardCategory> boardCategorys = boardCategoryRepository.findByUsingBoard(CommonConst.BOARD_NAME_FREE);
		
		model.addAttribute("boardFreeWrite", new BoardFreeWrite());
		model.addAttribute("boardCategorys", boardCategorys);
		
		return model;
	}
	
	public Integer getEdit(Model model, int seq, HttpServletResponse response) {
		
		CommonPrincipal principal = userService.getCommonPrincipal();
		String accountId = principal.getId();
		
		if (accountId == null) {
			return HttpServletResponse.SC_UNAUTHORIZED;
		}
		
		//BoardFreeWrite boardFreeWrite = boardFreeRepository.boardFreeWriteFindOneBySeq(seq);
		BoardFree boardFree = boardFreeRepository.findOneBySeq(seq);
		
		if (boardFree == null) {
			return HttpServletResponse.SC_NOT_FOUND;
		}
		
		if (boardFree.getWriter() == null) {
			return HttpServletResponse.SC_UNAUTHORIZED;
		}
		
		if (!accountId.equals(boardFree.getWriter().getUserId())) {
			return HttpServletResponse.SC_UNAUTHORIZED;
		}
		
		BoardFreeWrite boardFreeWrite = new BoardFreeWrite();
		
		logger.debug("boardFree=" + boardFree);
		
		if (boardFree.getGalleries() != null) {
			List<String> images = new ArrayList<String>();
			logger.debug("aaa");
			
			for (BoardImage gallery : boardFree.getGalleries()) {
				Gallery tempGallery = galleryRepository.findOne(gallery.getId());
				logger.debug("tempGallery=" + tempGallery);
				
				if (tempGallery != null) {
					Map<String, String> image = new HashMap<String, String>();
					
					image.put("uid", tempGallery.getId());
					image.put("name", tempGallery.getName());
					image.put("size", String.valueOf(tempGallery.getSize()));
					
					images.add(JSONObject.toJSONString(image));
				}
			}
			
			logger.debug("images=" + images);
			boardFreeWrite.setImages(images.toString());
		}
		
		boardFreeWrite.setId(boardFree.getId());
		boardFreeWrite.setSeq(boardFree.getSeq());
		boardFreeWrite.setCategoryName(boardFree.getCategoryName());
		boardFreeWrite.setSubject(boardFree.getSubject());
		boardFreeWrite.setContent(boardFree.getContent());
		boardFreeWrite.setViews(boardFree.getViews());
		boardFreeWrite.setWriter(boardFree.getWriter());
		
		List<BoardCategory> boardCategorys = boardCategoryRepository.findByUsingBoard(CommonConst.BOARD_NAME_FREE);
		
		model.addAttribute("boardFreeWrite", boardFreeWrite);
		model.addAttribute("boardCategorys", boardCategorys);
		
		return HttpServletResponse.SC_OK;
	}

	public void checkBoardFreeEdit(BoardFreeWrite boardFreeWrite, BindingResult result) {
		
		String id = boardFreeWrite.getId();
		
		if (id.isEmpty()) {
			result.rejectValue("id", "board.msg.cannot.edit");
		}
	}
	
	/**
	 * 자유게시판 글쓰기 데이터 DB에 삽입
	 * @param boardFree
	 */
	public void write(BoardFreeWrite boardFreeWrite) {
		
		BoardFree boardFree = new BoardFree();
		
		CommonPrincipal principal = userService.getCommonPrincipal();
		String userid = principal.getId();
		String username = principal.getUsername();
		String type = principal.getType();

		BoardWriter writer = new BoardWriter();
		writer.setUserId(userid);
		writer.setUsername(username);
		writer.setType(type);
		boardFree.setWriter(writer);
		boardFree.setSeq(commonService.getNextSequence(CommonConst.BOARD_NAME_FREE));
		
		List<BoardHistory> historys = new ArrayList<BoardHistory>();
		BoardHistory history = new BoardHistory();
		history.setId(new ObjectId().toString());
		history.setType(CommonConst.BOARD_HISTORY_TYPE_CREATE);
		history.setWriter(writer);
		historys.add(history);
		boardFree.setHistory(historys);
		
		JSONArray jsonArray = null;
		
		if (boardFreeWrite.getImages() != null) {
			JSONParser jsonParser = new JSONParser();
			try {			
				jsonArray = (JSONArray) jsonParser.parse(boardFreeWrite.getImages());
				List<BoardImage> galleries = new ArrayList<BoardImage>();
				
				for (int i = 0 ; i < jsonArray.size() ; i++) {
					JSONObject obj = (JSONObject)jsonArray.get(i);
					String id = (String) obj.get("uid");
					
					Gallery gallery = galleryRepository.findOne(id);
					
					if (gallery != null) {
						BoardImage boardImage = new BoardImage();
						boardImage.setId(gallery.getId());
						galleries.add(boardImage);
					}
				}
				
				if (galleries.size() > 0) {
					boardFree.setGalleries(galleries);
				}
				
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
		
		boardFree.setCategoryName(boardFreeWrite.getCategoryName());
		boardFree.setSubject(boardFreeWrite.getSubject());
		boardFree.setContent(boardFreeWrite.getContent());
		boardFree.setViews(boardFreeWrite.getViews());

		boardFreeRepository.save(boardFree);
		
		if (jsonArray != null) {
			BoardItem boardItem = new BoardItem();
			boardItem.setId(boardFree.getId());
			boardItem.setSeq(boardFree.getSeq());
			
			for (int i = 0 ; i < jsonArray.size() ; i++) {
				JSONObject obj = (JSONObject)jsonArray.get(i);
				String id = (String) obj.get("uid");

				Gallery gallery = galleryRepository.findOne(id);

				if (gallery != null) {
					gallery.setBoardItem(boardItem);
					galleryRepository.save(gallery);
				}
			}
		}

		if (logger.isInfoEnabled()) {
			logger.info("new post created. post seq=" + boardFree.getSeq() + ", subject=" + boardFree.getSubject());
		}

		if (logger.isDebugEnabled()) {
			logger.debug("boardFree(new) = " + boardFree);
		}
		
	}
	
	public void edit(BoardFreeWrite boardFreeWrite) {
		
		CommonPrincipal principal = userService.getCommonPrincipal();
		String userid = principal.getId();
		String username = principal.getUsername();
		String type = principal.getType();

		BoardWriter writer = new BoardWriter();
		writer.setUserId(userid);
		writer.setUsername(username);
		writer.setType(type);
		
		BoardFree boardFree = boardFreeRepository.findOne(boardFreeWrite.getId());
		boardFree.setCategoryName(boardFreeWrite.getCategoryName());
		boardFree.setSubject(boardFreeWrite.getSubject());
		boardFree.setContent(boardFreeWrite.getContent());
		
		List<BoardHistory> historys = boardFree.getHistory();
		
		if (historys == null) {
			historys = new ArrayList<BoardHistory>();
		}
		
		BoardHistory history = new BoardHistory();
		history.setId(new ObjectId().toString());
		history.setType(CommonConst.BOARD_HISTORY_TYPE_EDIT);
		history.setWriter(writer);
		historys.add(history);
		boardFree.setHistory(historys);
		
		JSONArray jsonArray = null;

		if (boardFreeWrite.getImages() != null) {
			JSONParser jsonParser = new JSONParser();
			try {			
				jsonArray = (JSONArray) jsonParser.parse(boardFreeWrite.getImages());
				List<BoardImage> galleries = new ArrayList<BoardImage>();

				for (int i = 0 ; i < jsonArray.size() ; i++) {
					JSONObject obj = (JSONObject)jsonArray.get(i);
					String id = (String) obj.get("uid");

					Gallery gallery = galleryRepository.findOne(id);

					if (gallery != null) {
						BoardImage boardImage = new BoardImage();
						boardImage.setId(gallery.getId());
						galleries.add(boardImage);
					}
				}

				if (galleries.size() > 0) {
					boardFree.setGalleries(galleries);
				}

			} catch (ParseException e) {
				e.printStackTrace();
			}
		}

		boardFreeRepository.save(boardFree);

		if (logger.isInfoEnabled()) {
			logger.info("post was edited. post seq=" + boardFree.getSeq() + ", subject=" + boardFree.getSubject());
		}

		if (logger.isDebugEnabled()) {
			logger.debug("BoardFree(edit) = " + boardFree);
		}
		
	}
	
	/**
	 * 자유게시판 목록 페이지
	 * @param model
	 * @param boardListInfo
	 * @return
	 */
	public Model getFree(Model model, Locale locale, BoardListInfo boardListInfo) {

		try {
			Map<String, Date> createDate = new HashMap<String, Date>();
			List<BoardFreeOnList> posts = new ArrayList<BoardFreeOnList>();
			ArrayList<Integer> seqs = new ArrayList<Integer>();
			Long numberPosts = (long) 0;
			
			Integer page = boardListInfo.getPage();
			String categoryName = boardListInfo.getCategory();
			
			if (page < 1) {
				page = 1;
				boardListInfo.setPage(page);
			}
			
			Sort sort = new Sort(Sort.Direction.DESC, Arrays.asList("seq"));
			Pageable pageable = new PageRequest(page - 1, CommonConst.BOARD_SIZE_LINE_NUMBER, sort);
			
			if (categoryName != null && 
					(categoryName.equals(CommonConst.BOARD_CATEGORY_NONE) || categoryName.equals(CommonConst.BOARD_CATEGORY_ALL))) {
				posts = boardFreeOnListRepository.findAll(pageable).getContent();
				numberPosts = boardFreeOnListRepository.count();
			} else {
				posts = boardFreeRepository.findByCategoryName(categoryName, pageable).getContent();
				numberPosts = boardFreeRepository.countByCategoryName(categoryName);
			}
			
			Pageable noticePageable = new PageRequest(0, 10, sort);
			List<BoardFreeOnList> notices = boardFreeRepository.findByNotice(noticePageable).getContent();
			
			BoardPageInfo boardPageInfo = commonService.getCountPages(page.longValue(), numberPosts, 5);
			
//			logger.debug("countAll=" + boardPageInfo);
			
			for (BoardFreeOnList tempPost : posts) {
				String tempId = tempPost.getId();
				Integer tempSeq = tempPost.getSeq();
				
				ObjectId objId = new ObjectId(tempId);
				createDate.put(tempId, objId.getDate());
				
				seqs.add(tempSeq);
			}
			
			for (BoardFreeOnList tempNotice : notices) {
				String tempId = tempNotice.getId();
				Integer tempSeq = tempNotice.getSeq();
				
				ObjectId objId = new ObjectId(tempId);
				createDate.put(tempId, objId.getDate());
				
				seqs.add(tempSeq);
			}
			
			List<BoardCategory> boardCategorys = boardCategoryRepository.findByUsingBoard(CommonConst.BOARD_NAME_FREE);
			
			HashMap<String, String> categorys = new HashMap<String, String>();
			categorys.put("all", "board.category.all");
			
			for (BoardCategory category : boardCategorys) {
				categorys.put(category.getName(), category.getResName());
			}
			
			HashMap<String, Integer> commentCount = boardFreeDAO.getBoardFreeCommentCount(seqs);
			HashMap<String, Integer> usersLikingCount = boardFreeDAO.getBoardFreeUsersLikingCount(seqs);
			HashMap<String, Integer> usersDislikingCount = boardFreeDAO.getBoardFreeUsersDislikingCount(seqs);
			HashMap<String, Integer> galleriesCount = boardFreeDAO.getBoardFreeGalleriesCount(seqs);
			
			model.addAttribute("posts", posts);
			model.addAttribute("notices", notices);
			model.addAttribute("categorys", categorys);
			model.addAttribute("pageInfo", boardPageInfo);
			model.addAttribute("boardListInfo", boardListInfo);
			model.addAttribute("commentCount", commentCount);
			model.addAttribute("usersLikingCount", usersLikingCount);
			model.addAttribute("usersDislikingCount", usersDislikingCount);
			model.addAttribute("galleriesCount", galleriesCount);
			model.addAttribute("createDate", createDate);
			model.addAttribute("dateTimeFormat", commonService.getDateTimeFormat(locale));
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return model;

	}
	
	/**
	 * 자유게시판 게시물 보기 페이지
	 * @param model
	 * @param seq
	 * @param boardListInfo
	 * @return
	 */
	public Integer getFreeView(Model model, Locale locale, int seq, BoardListInfo boardListInfo, Boolean isAddCookie) {
		
		try {
			BoardFree boardFree = boardFreeRepository.findOneBySeq(seq);
			List<BoardImage> images = boardFree.getGalleries();
			
			if (boardFree == null) {
				return HttpServletResponse.SC_UNAUTHORIZED;
			}
			
			BoardCategory boardCategory = boardCategoryRepository.findByName(boardFree.getCategoryName());
			
			if (isAddCookie == true) {
				int views = boardFree.getViews();
				boardFree.setViews(++views);
				boardFreeRepository.save(boardFree);
			}
			
			LocalDate date = LocalDate.now();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			DateTimeFormatter format = DateTimeFormatter.ISO_DATE;
			
			Long timeNow = sdf.parse(date.format(format)).getTime();
			
			if (images != null) {
				List<Gallery> galleries = new ArrayList<Gallery>();
				
				for(BoardImage image : images) {
					Gallery gallery = galleryRepository.findOne(image.getId());
					
					if (gallery != null) {
						galleries.add(gallery);
					}
				}
				
				if (galleries.size() > 0) {
					model.addAttribute("galleries", galleries);
				}
			}
			
			model.addAttribute("post", boardFree);
			model.addAttribute("category", boardCategory);
			model.addAttribute("timeNow", timeNow);
			model.addAttribute("listInfo", boardListInfo);
			model.addAttribute("dateTimeFormat", commonService.getDateTimeFormat(locale));
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	/**
	 * 게시물 좋아요 싫어요
	 * @param model
	 * @param seq
	 * @param status
	 * @return
	 */
	public Model setUsersFeelings(Model model, int seq, String feeling) {
		
		String errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_NONE;

		CommonPrincipal principal = userService.getCommonPrincipal();
		String userid = principal.getId();
		String username = principal.getUsername();
		
		BoardFree boardFree = boardFreeRepository.findOneBySeq(seq);
		BoardWriter writer = boardFree.getWriter();

		List<BoardUser> usersLiking = boardFree.getUsersLiking();
		List<BoardUser> usersDisliking = boardFree.getUsersDisliking();

		if (usersLiking == null) {
			usersLiking = new ArrayList<BoardUser>(); 
		}
		
		if (usersDisliking == null) {
			usersDisliking = new ArrayList<BoardUser>(); 
		}
		
		if (userid != null && username != null) {
			if (writer != null && userid.equals(writer.getUserId())) {
				errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_WRITER;
			}

			if (errCode.equals(CommonConst.BOARD_USERS_FEELINGS_STATUS_NONE)) {
				for (BoardUser boardUser : usersLiking) {
					if (boardUser != null && userid.equals(boardUser.getUserId())) {
						errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_ALREADY;
						break;
					}
				}
			}
			
			if (errCode.equals(CommonConst.BOARD_USERS_FEELINGS_STATUS_NONE)) {
				for (BoardUser boardUser : usersDisliking) {
					if (boardUser != null && userid.equals(boardUser.getUserId())) {
						errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_ALREADY;
						break;
					}
				}
			}

			if (errCode.equals(CommonConst.BOARD_USERS_FEELINGS_STATUS_NONE)) {
				BoardUser boardUser = new BoardUser();
				boardUser.setUserId(userid);
				boardUser.setUsername(username);
				boardUser.setId(new ObjectId().toString());

				switch (feeling) {
				case CommonConst.BOARD_USERS_FEELINGS_TYPE_LIKE:
					usersLiking.add(boardUser);
					boardFree.setUsersLiking(usersLiking);
					errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_LIKE; 
					break;
				case CommonConst.BOARD_USERS_FEELINGS_TYPE_DISLIKE:
					usersDisliking.add(boardUser);
					boardFree.setUsersDisliking(usersDisliking);
					errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_DISLIKE;
					break;
				default:
					break;
				}
				
				boardFreeRepository.save(boardFree);
			}
		} else {
			errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_ANONYMOUS;
		}
		
		model.addAttribute("errorCode", errCode);
		model.addAttribute("numberOfLike", usersLiking.size());
		model.addAttribute("numberOfDislike", usersDisliking.size());
		
		return model;
	}
	
	public void freeCommentWrite(Integer seq, String content) {
		BoardFreeComment boardFreeComment = new BoardFreeComment();
		
		if (!commonService.isAnonymousUser()) {
			BoardFreeOnComment boardFreeOnComment = boardFreeRepository.boardFreeOnCommentFindBySeq(seq);
			
			BoardItem boardItem = new BoardItem();
			boardItem.setId(boardFreeOnComment.getId());
			boardItem.setSeq(boardFreeOnComment.getSeq());
			
			boardFreeComment.setBoardItem(boardItem);
			
			CommonPrincipal principal = userService.getCommonPrincipal();
			BoardWriter writer = new BoardWriter();
			writer.setUserId(principal.getId());
			writer.setUsername(principal.getUsername());
			writer.setType(principal.getType());
			boardFreeComment.setWriter(writer);
			
			boardFreeComment.setContent(content);
			
			boardFreeCommentRepository.save(boardFreeComment);
		} else {
			
		}
	}
	
	public void getFreeComment(Model model, int seq, Integer page, Integer size) {
		BoardFreeOnComment boardFreeOnComment = boardFreeRepository.boardFreeOnCommentFindBySeq(seq);
		
		BoardItem boardItem = new BoardItem();
		boardItem.setId(boardFreeOnComment.getId());
		boardItem.setSeq(boardFreeOnComment.getSeq());
		
		if (page == null) page = 1;
		
		Sort sort = new Sort(Sort.Direction.ASC, Arrays.asList("_id"));
		Pageable pageable = new PageRequest(page - 1, size, sort);
		
		List<BoardFreeComment> comments = boardFreeCommentRepository.findByBoardItem(boardItem, pageable).getContent();
		Integer count = boardFreeCommentRepository.countByBoardItem(boardItem);
		
		model.addAttribute("comments", comments);
		model.addAttribute("count", count);		
	}
	
	public void getFreeCommentCount(Model model, int seq) {
		BoardFreeOnComment boardFreeOnComment = boardFreeRepository.boardFreeOnCommentFindBySeq(seq);
		
		BoardItem boardItem = new BoardItem();
		boardItem.setId(boardFreeOnComment.getId());
		boardItem.setSeq(boardFreeOnComment.getSeq());
		
		Integer count = boardFreeCommentRepository.countByBoardItem(boardItem);
		
		model.addAttribute("count", count);
	}
	
	public Model setUsersCommentFeelings(Model model, int seq, String id, String feeling) {
		
		String errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_NONE;

		CommonPrincipal principal = userService.getCommonPrincipal();
		String userid = principal.getId();
		String username = principal.getUsername();
		
		BoardFreeComment boardComment = boardFreeCommentRepository.findById(id);
		BoardWriter writer = boardComment.getWriter();

		List<BoardUser> usersLiking = boardComment.getUsersLiking();
		List<BoardUser> usersDisliking = boardComment.getUsersDisliking();

		if (usersLiking == null) {
			usersLiking = new ArrayList<BoardUser>(); 
		}
		
		if (usersDisliking == null) {
			usersDisliking = new ArrayList<BoardUser>(); 
		}
		
		if (userid != null && username != null) {
			
			if (userid.equals(writer.getUserId())) {
				errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_WRITER;
			}

			if (errCode.equals(CommonConst.BOARD_USERS_FEELINGS_STATUS_NONE)) {
				for (BoardUser boardUser : usersLiking) {
					if (boardUser != null && userid.equals(boardUser.getUserId())) {
						errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_ALREADY;
						break;
					}
				}
			}
			
			if (errCode.equals(CommonConst.BOARD_USERS_FEELINGS_STATUS_NONE)) {
				for (BoardUser boardUser : usersDisliking) {
					if (boardUser != null && userid.equals(boardUser.getUserId())) {
						errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_ALREADY;
						break;
					}
				}
			}

			if (errCode.equals(CommonConst.BOARD_USERS_FEELINGS_STATUS_NONE)) {
				BoardUser boardUser = new BoardUser();
				boardUser.setUserId(userid);
				boardUser.setUsername(username);
				boardUser.setId(new ObjectId().toString());

				switch (feeling) {
				case CommonConst.BOARD_USERS_FEELINGS_TYPE_LIKE:
					usersLiking.add(boardUser);
					boardComment.setUsersLiking(usersLiking);
					errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_LIKE; 
					break;
				case CommonConst.BOARD_USERS_FEELINGS_TYPE_DISLIKE:
					usersDisliking.add(boardUser);
					boardComment.setUsersDisliking(usersDisliking);
					errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_DISLIKE;
					break;
				default:
					break;
				}
				
				boardFreeCommentRepository.save(boardComment);
			}
		} else {
			errCode = CommonConst.BOARD_USERS_FEELINGS_STATUS_ANONYMOUS;
		}
		
		model.addAttribute("errorCode", errCode);
		model.addAttribute("numberOfLike", usersLiking.size());
		model.addAttribute("numberOfDislike", usersDisliking.size());
		
		return model;
	}
	
	public Integer deleteFree(Model model, int seq, String type) {
		
		CommonPrincipal principal = userService.getCommonPrincipal();
		String accountId = principal.getId();
		String accountUsername = principal.getUsername();
		String accountType = principal.getType();

		BoardWriter writer = new BoardWriter();
		writer.setUserId(accountId);
		writer.setUsername(accountUsername);
		writer.setType(accountType);
		
		if (accountId == null) {
			return HttpServletResponse.SC_UNAUTHORIZED;
		}
		
		BoardFree boardFree = boardFreeRepository.findOneBySeq(seq);
		
		if (boardFree.getWriter() == null) {
			return HttpServletResponse.SC_UNAUTHORIZED;
		}
		
		if (!accountId.equals(boardFree.getWriter().getUserId())) {
			return HttpServletResponse.SC_UNAUTHORIZED;
		}
		
		BoardItem boardItem = new BoardItem();
		boardItem.setId(boardFree.getId());
		boardItem.setSeq(boardFree.getSeq());
		
		Integer count = boardFreeCommentRepository.countByBoardItem(boardItem);
		
		if (type.equals(CommonConst.BOARD_DELETE_TYPE_POSTONLY) && count < 1) {
			return HttpServletResponse.SC_NOT_ACCEPTABLE;
		} else if (type.equals(CommonConst.BOARD_DELETE_TYPE_ALL) && count > 0) {
			return HttpServletResponse.SC_NOT_ACCEPTABLE;
		}
		
		switch (type) {
		case CommonConst.BOARD_DELETE_TYPE_POSTONLY:
			
			boardFree.setContent(null);
			boardFree.setSubject(null);
			boardFree.setWriter(null);
			
			List<BoardHistory> historys = boardFree.getHistory();
			
			if (historys == null) {
				historys = new ArrayList<BoardHistory>();
			}
			
			BoardHistory history = new BoardHistory();
			history.setId(new ObjectId().toString());
			history.setType(CommonConst.BOARD_HISTORY_TYPE_DELETE);
			history.setWriter(writer);
			historys.add(history);
			boardFree.setHistory(historys);
			
			BoardStatus boardStatus = boardFree.getStatus();
			
			if (boardStatus == null) {
				boardStatus = new BoardStatus();
			}
			
			boardStatus.setDelete("delete");
			boardFree.setStatus(boardStatus);
			
			boardFreeRepository.save(boardFree);

			if (logger.isInfoEnabled()) {
				logger.info("A post was deleted(post only). post seq=" + boardFree.getSeq() + ", subject=" + boardFree.getSubject());
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Delete(post only) post. BoardFree = " + boardFree);
			}
			
			break;
			
		case CommonConst.BOARD_DELETE_TYPE_ALL:
			boardFreeRepository.delete(boardFree);

			if (logger.isInfoEnabled()) {
				logger.info("A post was deleted(all). post seq=" + boardFree.getSeq() + ", subject=" + boardFree.getSubject());
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Delete(all) post. BoardFree = " + boardFree);
			}
			
			break;
		}
		
		return HttpServletResponse.SC_OK;
	}
	
	public Integer setNotice(int seq, String type) {
		
		if (!commonService.isAdmin()) {
			return HttpServletResponse.SC_UNAUTHORIZED;
		}
		
		CommonPrincipal principal = userService.getCommonPrincipal();
		String accountId = principal.getId();
		String accountUsername = principal.getUsername();
		String accountType = principal.getType();

		BoardWriter writer = new BoardWriter();
		writer.setUserId(accountId);
		writer.setUsername(accountUsername);
		writer.setType(accountType);
		
		if (accountId == null) {
			return HttpServletResponse.SC_UNAUTHORIZED;
		}
		
		BoardFree boardFree = boardFreeRepository.findOneBySeq(seq);
		BoardStatus status = boardFree.getStatus();
		
		if (status == null) {
			status = new BoardStatus();
		}
		
		String notice = status.getNotice();
		String noticeType = ""; 
		
		switch (type) {
		case CommonConst.COMMON_TYPE_SET:
			if (notice != null && notice.equals(CommonConst.BOARD_HISTORY_TYPE_NOTICE)) {			
				return HttpServletResponse.SC_NOT_ACCEPTABLE;
			}
			
			status.setNotice(CommonConst.BOARD_HISTORY_TYPE_NOTICE);
			noticeType = CommonConst.BOARD_HISTORY_TYPE_NOTICE;
			break;
			
		case CommonConst.COMMON_TYPE_CANCEL:
			if (notice == null) {
				return HttpServletResponse.SC_NOT_ACCEPTABLE;
			}
			
			status.setNotice(null);
			noticeType = CommonConst.BOARD_HISTORY_TYPE_CANCEL_NOTICE;
			break;
		}
		
		boardFree.setStatus(status);
		
		if (!noticeType.isEmpty()) {
			List<BoardHistory> historys = boardFree.getHistory();
			
			if (historys == null) {
				historys = new ArrayList<BoardHistory>();
			}
			
			BoardHistory history = new BoardHistory();
			history.setId(new ObjectId().toString());
			history.setType(noticeType);
			history.setWriter(writer);
			historys.add(history);
			boardFree.setHistory(historys);
		}
		
		boardFreeRepository.save(boardFree);
		
		if (logger.isInfoEnabled()) {
			logger.info("Set notice. post seq=" + boardFree.getSeq() + ", type=" + status.getNotice());
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Set notice. BoardFree = " + boardFree);
		}
		
		return HttpServletResponse.SC_OK;
	}
}
