package com.jakduk.api.board;

import com.jakduk.api.TestMvcConfig;
import com.jakduk.api.common.AuthHelper;
import com.jakduk.api.common.Constants;
import com.jakduk.api.common.board.category.BoardCategory;
import com.jakduk.api.common.board.category.BoardCategoryGenerator;
import com.jakduk.api.common.util.JakdukUtils;
import com.jakduk.api.common.util.ObjectMapperUtils;
import com.jakduk.api.model.aggregate.BoardTop;
import com.jakduk.api.model.db.Article;
import com.jakduk.api.model.db.Gallery;
import com.jakduk.api.model.embedded.ArticleCommentStatus;
import com.jakduk.api.model.embedded.ArticleStatus;
import com.jakduk.api.model.embedded.CommonWriter;
import com.jakduk.api.model.embedded.LocalSimpleName;
import com.jakduk.api.model.simple.ArticleOnSearch;
import com.jakduk.api.model.simple.ArticleSimple;
import com.jakduk.api.restcontroller.BoardRestController;
import com.jakduk.api.restcontroller.vo.board.*;
import com.jakduk.api.service.ArticleService;
import com.jakduk.api.service.GalleryService;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(BoardRestController.class)
@Import(TestMvcConfig.class)
@AutoConfigureRestDocs(outputDir = "build/snippets")
public class BoardRestControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockBean private RestTemplateBuilder restTemplateBuilder;
    @MockBean private ArticleService articleService;
    @MockBean private GalleryService galleryService;
    @MockBean private AuthHelper authHelper;

    private CommonWriter commonWriter;
    private Article article;
    private BoardCategory boardCategory;
    private Map<String, String> categoriesMap;

    @Before
    public void setUp(){
        commonWriter = CommonWriter.builder()
                .userId("userid01")
                .username("user01")
                .providerId(Constants.ACCOUNT_TYPE.JAKDUK)
                .build();

        article = Article.builder()
                .id("boardFreeId01")
                .seq(1)
                .writer(commonWriter)
                .subject("제목입니다.")
                .content("내용입니다.")
                .board(Constants.BOARD_TYPE.FOOTBALL.name())
                .category("CLASSIC")
                .linkedGallery(true)
                .status(ArticleStatus.builder().notice(false).delete(false).device(Constants.DEVICE_TYPE.NORMAL).build())
                .build();

        List<BoardCategory> categories = new BoardCategoryGenerator().getCategories(Constants.BOARD_TYPE.FOOTBALL, JakdukUtils.getLocale());

        boardCategory = categories.get(0);

        categoriesMap = categories.stream()
                .collect(Collectors.toMap(BoardCategory::getCode, boardCategory -> boardCategory.getNames().get(0).getName()));

        categoriesMap.put("ALL", JakdukUtils.getResourceBundleMessage("messages.board", "board.category.all"));
    }

    @Test
    @WithMockUser
    public void getArticlesTest() throws Exception {

        BoardGallerySimple gallerySimple = BoardGallerySimple.builder()
                .id("58b9050b807d714eaf50a111")
                .thumbnailUrl("https://dev-web.jakduk.com/api/gallery/thumbnail/58b9050b807d714eaf50a111")
                .build();

        GetArticle article = GetArticle.builder()
                .id("58b7b9dd716dce06b10e449a")
                .board(Constants.BOARD_TYPE.FOOTBALL.name())
                .writer(commonWriter)
                .subject("글 제목입니다.")
                .seq(2)
                .category(boardCategory.getCode())
                .views(10)
                .status(new ArticleStatus(false, false, Constants.DEVICE_TYPE.NORMAL))
                .galleries(Arrays.asList(gallerySimple))
                .shortContent("본문입니다. (100자)")
                .commentCount(5)
                .likingCount(3)
                .dislikingCount(1)
                .build();

        GetArticle notice = GetArticle.builder()
                .id("58b7b9dd716dce06b10e449a")
                .board(Constants.BOARD_TYPE.FOOTBALL.name())
                .writer(commonWriter)
                .subject("공지글 제목입니다.")
                .seq(3)
                .category(boardCategory.getCode())
                .views(15)
                .status(new ArticleStatus(true, false, Constants.DEVICE_TYPE.NORMAL))
                .galleries(Arrays.asList(gallerySimple))
                .shortContent("본문입니다. (100자)")
                .commentCount(8)
                .likingCount(10)
                .dislikingCount(2)
                .build();

        GetArticlesResponse expectResponse = GetArticlesResponse.builder()
                .categories(categoriesMap)
                .articles(Arrays.asList(article))
                .notices(Arrays.asList(notice))
                .last(false)
                .first(true)
                .totalPages(50)
                .size(20)
                .number(0)
                .numberOfElements(20)
                .totalElements(1011L)
                .build();

        when(articleService.getArticles(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(expectResponse);

        mvc.perform(
                get("/api/board/{board}/articles", Constants.BOARD_TYPE_LOWERCASE.football)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(ObjectMapperUtils.writeValueAsString(expectResponse)))
                .andDo(document("getArticles",
                        pathParameters(
                                parameterWithName("board").description("게시판 " + Stream.of(Constants.BOARD_TYPE_LOWERCASE.values()).map(Enum::name).collect(Collectors.toList()))
                        ),
                        requestParameters(
                                parameterWithName("page").description("페이지 번호(1부터 시작)").optional(),
                                parameterWithName("size").description("페이지 사이즈").optional(),
                                parameterWithName("categoryCode").description("말머리").optional()
                        ),
                        responseFields(
                                fieldWithPath("categories").type(JsonFieldType.OBJECT).description("말머리 맵"),
                                fieldWithPath("articles").type(JsonFieldType.ARRAY).description("글 목록"),
                                fieldWithPath("notices").type(JsonFieldType.ARRAY).description("공지글 목록"),
                                fieldWithPath("last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부"),
                                fieldWithPath("first").type(JsonFieldType.BOOLEAN).description("첫 페이지 여부"),
                                fieldWithPath("totalPages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                                fieldWithPath("size").type(JsonFieldType.NUMBER).description("페이지당 글 수"),
                                fieldWithPath("number").type(JsonFieldType.NUMBER).description("현재 페이지(0부터 시작)"),
                                fieldWithPath("numberOfElements").type(JsonFieldType.NUMBER).description("현제 페이지에서 글 수"),
                                fieldWithPath("totalElements").type(JsonFieldType.NUMBER).description("전체 글 수")
                        )
                ));
    }

    @Test
    @WithMockUser
    public void getFreePostsTopsTest() throws Exception {

        List<BoardTop> expectTopLikes = Arrays.asList(
                BoardTop.builder()
                        .id("boardFreeId01")
                        .seq(1)
                        .subject("인기있는 글 제목")
                        .count(5)
                        .views(100)
                        .build()
        );

        when(articleService.getFreeTopLikes(anyString(), any(ObjectId.class)))
                .thenReturn(expectTopLikes);

        List<BoardTop> expectTopComments = Arrays.asList(
                BoardTop.builder()
                        .id("boardFreeId02")
                        .seq(2)
                        .subject("댓글많은 글 제목")
                        .count(10)
                        .views(150)
                        .build()
        );

        when(articleService.getFreeTopComments(anyString(), any(ObjectId.class)))
                .thenReturn(expectTopComments);

        GetBoardTopsResponse response = GetBoardTopsResponse.builder()
                .topLikes(expectTopLikes)
                .topComments(expectTopComments)
                .build();

        mvc.perform(get("/api/board/free/tops")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(ObjectMapperUtils.writeValueAsString(response)));
    }

    @Test
    @WithMockUser
    public void getFreeCommentsTest() throws Exception {

        GetArticleCommentsResponse response = GetArticleCommentsResponse.builder()
                .comments(
                        Arrays.asList(
                                GetArticleComment.builder()
                                        .id("boardFreeCommentId01")
                                        .article(
                                                ArticleOnSearch.builder()
                                                        .id(article.getId())
                                                        .seq(article.getSeq())
                                                        .subject(article.getSubject())
                                                        .build())
                                        .writer(commonWriter)
                                        .content("댓글 내용입니다.")
                                        .status(new ArticleCommentStatus(Constants.DEVICE_TYPE.NORMAL))
                                        .numberOfDislike(5)
                                        .numberOfDislike(3)
                                        .build()
                        )
                )
                .last(true)
                .first(true)
                .totalPages(1)
                .size(10)
                .number(0)
                .numberOfElements(1)
                .totalElements(1)
                .build();

        when(articleService.getArticleComments(any(CommonWriter.class), anyString(), anyInt(), anyInt()))
                .thenReturn(response);

        mvc.perform(get("/api/board/free/comments")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(ObjectMapperUtils.writeValueAsString(response)));

    }

    @Test
    @WithMockUser
    public void getFreePostTest() throws Exception {

        ArticleDetail articleDetail = new ArticleDetail();
        BeanUtils.copyProperties(article, articleDetail);
        articleDetail.setCategory(boardCategory);
        articleDetail.setNumberOfLike(5);
        articleDetail.setNumberOfDislike(4);
        articleDetail.setGalleries(
                Arrays.asList(
                        ArticleGallery.builder()
                        .id("boardGalleryId01")
                        .name("성남FC 시즌권 사진")
                        .imageUrl("https://dev-web.jakduk.com/api/gallery/58b9050b807d714eaf50a111")
                        .thumbnailUrl("https://dev-web.jakduk.com/api/gallery/thumbnail/58b9050b807d714eaf50a111")
                        .build()
                )
        );

        ArticleSimple prevPost = ArticleSimple.builder()
                .id("boardFreeId02")
                .seq(2)
                .subject("이전 글 제목")
                .writer(commonWriter)
                .build();

        ArticleSimple nextPost = ArticleSimple.builder()
                .id("boardFreeId03")
                .seq(3)
                .subject("다음 글 제목")
                .writer(commonWriter)
                .build();

        GetArticleDetailResponse response = GetArticleDetailResponse.builder()
                .article(articleDetail)
                .prevArticle(prevPost)
                .nextArticle(nextPost)
                .build();

        when(articleService.getArticleDetail(anyString(), anyInt(), anyBoolean()))
                .thenReturn(ResponseEntity.ok().body(response));

        mvc.perform(get("/api/board/free/{seq}", article.getSeq())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(ObjectMapperUtils.writeValueAsString(response)));
    }

    @Test
    @WithMockUser
    public void addFreePostTest() throws Exception {

        GalleryOnBoard galleryOnBoard = GalleryOnBoard.builder().id("galleryid01").name("공차는사진").build();

        WriteArticle form = WriteArticle.builder()
                .subject("제목입니다.")
                .content("내용입니다.")
                .categoryCode("CLASSIC")
                .galleries(Arrays.asList(galleryOnBoard))
                .build();

        when(authHelper.getCommonWriter(any(Authentication.class)))
                .thenReturn(commonWriter);

        List<Gallery> expectGalleries = Arrays.asList(
                Gallery.builder()
                        .id(galleryOnBoard.getId())
                        .name(galleryOnBoard.getName())
                        .fileName("galleryFileName01")
                        .contentType("image/jpeg")
                        .writer(commonWriter)
                        .hash("HEXVALUE")
                        .build()
        );

        when(galleryService.findByIdIn(any()))
                .thenReturn(expectGalleries);

        when(articleService.insertArticle(any(CommonWriter.class), any(Constants.BOARD_TYPE.class), anyString(), anyString(), anyString(),
                anyListOf(Gallery.class), any(Constants.DEVICE_TYPE.class)))
                .thenReturn(article);

        doNothing().when(galleryService)
                .processLinkedGalleries(anyListOf(Gallery.class), anyListOf(GalleryOnBoard.class), anyListOf(String.class),
                        any(Constants.GALLERY_FROM_TYPE.class), anyString());

        WriteArticleResponse expectResponse = WriteArticleResponse.builder()
                .seq(article.getSeq())
                .build();

        mvc.perform(post("/api/board/free")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ObjectMapperUtils.writeValueAsString(form)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(ObjectMapperUtils.writeValueAsString(expectResponse)));
    }

}
