package cn.ninanina.wushan.service.impl;

import cn.ninanina.wushan.common.util.LuceneUtil;
import cn.ninanina.wushan.domain.Comment;
import cn.ninanina.wushan.domain.User;
import cn.ninanina.wushan.domain.VideoDetail;
import cn.ninanina.wushan.repository.CommentRepository;
import cn.ninanina.wushan.repository.UserRepository;
import cn.ninanina.wushan.repository.VideoRepository;
import cn.ninanina.wushan.service.VideoService;
import cn.ninanina.wushan.service.cache.RecommendCacheManager;
import cn.ninanina.wushan.service.cache.VideoAudienceCacheManager;
import cn.ninanina.wushan.service.cache.VideoCacheManager;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Version;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;

/**
 * <p>注意：启动项目前需要先启动chrome。
 * <p>第一次开启chrome的命令：
 * <p>   nohup /opt/google/chrome/google-chrome --no-sandbox
 * --remote-debugging-port=9222 --user-data-dir="/home/data/chrome" &
 * <p>如果没有删除/home/data/chrome的数据，以后开启chrome可以添加--headless来节省内存
 * <p>保证使程序在9222端口操作chrome，并且后台运行
 */
@Service
@Slf4j
public class VideoServiceImpl implements VideoService {

    @Autowired
    private VideoCacheManager videoCacheManager;

    @Autowired
    private RecommendCacheManager recommendCacheManager;

    @Autowired
    private VideoAudienceCacheManager audienceCacheManager;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommentRepository commentRepository;

    private WebDriver driver;

    @PostConstruct
    public void init() {
        System.setProperty("webdriver.chrome.driver", "/opt/WebDriver/bin/chromedriver");
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("debuggerAddress", "127.0.0.1:9222");
        driver = new ChromeDriver(options);
        log.info("webdriver initialized. now title: {}", driver.getTitle());
    }

    @Override
    public List<VideoDetail> recommendVideos(@Nonnull User user, @Nonnull String appKey, int limit) {
        List<VideoDetail> viewedVideos = user.getViewedVideos();
        List<VideoDetail> collectedVideos = user.getCollectedVideos();
        List<VideoDetail> downloadedVideos = user.getDownloadedVideos();
        if (CollectionUtils.isEmpty(viewedVideos)
                && CollectionUtils.isEmpty(collectedVideos)
                && CollectionUtils.isEmpty(downloadedVideos)) {
            return randomHotVideos(appKey, limit);
        }

        return null;
    }

    @Override
    public List<VideoDetail> randomHotVideos(@Nonnull String appKey, @Nonnull Integer limit) {
        List<Long> videoIdList = videoCacheManager.getKeys();
        List<VideoDetail> random10Videos = new ArrayList<>(limit);
        List<VideoDetail> result = new ArrayList<>(limit);
        //videoIdList是一个linkedList，长度小于3w，遍历一次id列表，随机取其中10个即可。
        Set<Integer> seqToFetch = new HashSet<>();
        Random random = new Random();
        while (seqToFetch.size() < limit) {
            int seq = random.nextInt(videoIdList.size());
            seqToFetch.add(seq);
        }
        int i = 0;
        for (Long id : videoIdList) {
            if (seqToFetch.contains(i)) {
                random10Videos.add(videoCacheManager.getVideo(id));
            }
            i++;
        }
        //如果重复了，替换
        //替换方法是，遍历videoIdList，如果遍历到的id没有被推荐过，那就推荐它
        //这样保证了最火热的视频优先推荐
        for (VideoDetail videoDetail : random10Videos) {
            if (!recommendCacheManager.exist(appKey, videoDetail.getId())) {
                result.add(videoDetail);
                recommendCacheManager.save(appKey,videoDetail.getId());
            } else {
                for (Long id : videoIdList) {
                    if (!recommendCacheManager.exist(appKey, id)) {
                        result.add(videoCacheManager.getVideo(id));
                        recommendCacheManager.save(appKey,id);
                        break;
                    }
                }
            }
        }
        log.info("user with appKey {} get random hot videos, total: {}",
                appKey, recommendCacheManager.total(appKey));
        return result;
    }

    @Override
    public VideoDetail getVideoDetail(long videoId, User user) {
        VideoDetail detail = videoCacheManager.getVideo(videoId);
        if (StringUtils.isEmpty(detail.getSrc()) || !detail.getSrc().contains("xvideos")) {
            detail.setSrc(getSrc(detail.getUrl()));
            log.info("new video src: {}", detail.getSrc());
            videoCacheManager.saveVideo(detail);
            videoRepository.save(detail);
        }
        String src = detail.getSrc(); //这里要保证视频链接真实，虽然视频不一定有效
        log.info("video {}, src {}", videoId, src);
        long currentSeconds = System.currentTimeMillis() / 1000;
        long urlSeconds = Long.parseLong(src.substring(src.indexOf("?e=") + 3, src.indexOf("&h=")));
        long interval = 3600 * 5 / 2; //规定视频失效时间为2.5小时
        if (currentSeconds - urlSeconds >= interval) {
            detail.setSrc(getSrc(detail.getUrl()));
            log.info("update video src, videoId: {}, newSrc: {}", videoId, detail.getSrc());
            videoCacheManager.saveVideo(detail);
            videoRepository.save(detail);
        }
        if (user != null) {
            List<VideoDetail> viewedVideos = user.getViewedVideos();
            if (CollectionUtils.isEmpty(viewedVideos)) {
                viewedVideos = new ArrayList<>();
                user.setViewedVideos(viewedVideos);
            }
            viewedVideos.add(detail);
            userRepository.save(user);
        }
        audienceCacheManager.increase(videoId);
        return detail;
    }

    //TODO:获取相关视频，高优先级
    @Override
    public List<VideoDetail> relatedVideos(long videoId, int limit) {
        return null;
    }

    //TODO:设置同义词
    @SneakyThrows
    @Override
    public List<VideoDetail> search(@Nonnull String word, @Nonnull Integer offset, @Nonnull Integer limit) {
        List<VideoDetail> result = new ArrayList<>();
        IndexSearcher indexSearcher = LuceneUtil.get().getIndexSearcher();
        String[] fields = {"title", "titleZh", "tagZh"};
        QueryParser parser = new MultiFieldQueryParser(Version.LUCENE_4_10_4, fields, LuceneUtil.get().getAnalyzer());
        Query query = parser.parse(word);
        TopDocs topDocs = indexSearcher.search(query, offset + limit);
        for (int i = offset; i < offset + limit; i++) {
            ScoreDoc scoreDoc = topDocs.scoreDocs[i];
            Document document = indexSearcher.doc(scoreDoc.doc);
            long id = Long.parseLong(document.get("id"));
            VideoDetail videoDetail = videoRepository.getOne(id);
            result.add(videoDetail);
        }
        return result;
    }

    @Override
    public Comment commentOn(@Nonnull User user, @Nonnull Long videoId, @Nonnull String content, @Nullable Long parentId) {
        Comment comment = new Comment();
        comment.setContent(content);
        //被评论的视频需要放到热点视频中
        VideoDetail videoDetail = videoCacheManager.getVideo(videoId);
        if (videoDetail == null) return null;
        comment.setVideo(videoDetail);
        comment.setUser(user);
        comment.setApproved(0);
        if (parentId != null) comment.setParentId(parentId);
        comment.setTime(System.currentTimeMillis());
        comment = commentRepository.save(comment);
        return comment;
    }

    @Override
    public Boolean collect(@Nonnull User user, @Nonnull Long videoId) {
        List<VideoDetail> collectedVideos = user.getCollectedVideos();
        if (CollectionUtils.isEmpty(collectedVideos)) {
            collectedVideos = new ArrayList<>();
            user.setCollectedVideos(collectedVideos);
        }
        VideoDetail videoDetail = videoCacheManager.getVideo(videoId);
        //已收藏过，则移除
        if (collectedVideos.contains(videoDetail)) {
            collectedVideos.remove(videoDetail);
            userRepository.save(user);
            log.info("user canceled collection, user id: {}, video id: {}", user.getId(), videoId);
            return false;
        }
        collectedVideos.add(videoDetail);
        log.info("user collected video, user id: {}, video id: {}", user.getId(), videoId);
        userRepository.save(user);
        return true;
    }

    @Override
    public List<VideoDetail> collectedVideos(User user) {
        return user.getCollectedVideos();
    }

    //TODO:分页获取看过的video，高优先级
    @Override
    public List<VideoDetail> viewedVideos(@Nonnull User user, @Nonnull Integer offset, @Nonnull Integer limit) {
        return null;
    }

    @Override
    public void download(@Nonnull User user, @Nonnull Long videoId) {
        VideoDetail videoDetail = videoRepository.getOne(videoId);
        List<VideoDetail> downloadedVideos = user.getDownloadedVideos();
        if (downloadedVideos == null) {
            downloadedVideos = new ArrayList<>();
            user.setDownloadedVideos(downloadedVideos);
        }
        downloadedVideos.add(videoDetail);
        userRepository.save(user);
    }

    @Override
    public void exitDetail(@Nonnull Long videoId) {
        audienceCacheManager.decrease(videoId);
    }

    @Override
    public int audiences(@Nonnull Long videoId) {
        return audienceCacheManager.get(videoId);
    }

    @Override
    public List<Pair<VideoDetail, Integer>> onlineRank(@Nonnull Integer limit) {
        List<Pair<VideoDetail, Integer>> result = new ArrayList<>();
        List<Pair<Long, Integer>> rank = audienceCacheManager.rank(limit);
        for (Pair<Long, Integer> original : rank) {
            VideoDetail videoDetail = videoCacheManager.getVideo(original.getLeft());
            result.add(Pair.of(videoDetail, original.getRight()));
        }
        return result;
    }

    private synchronized String getSrc(String url) {
        String result = "";
        driver.get(url);

        //点击下载按钮
        By downloadBtn = new By.ByCssSelector("#video-actions > ul > li:nth-child(2) > a");
        if (isElementExist(driver, downloadBtn)) {
            driver.findElement(downloadBtn).click();
        }

        //等待下载链接
        By href = new By.ByCssSelector("#tabDownload > p > a:nth-child(1)");
        if (isElementExist(driver, href)) {
            result = driver.findElement(href).getAttribute("href");
            log.info("get newest video src, url: {}, src: {}", url, result);
        } else {
            log.error("can't get download href!, errMsg: {}",
                    driver.findElement(new By.ByCssSelector("#tabDownload > h4")).getText());
        }
        return result;
    }

    private boolean isElementExist(WebDriver driver, By locator) {
        driver.manage().timeouts().implicitlyWait(3, TimeUnit.SECONDS);
        try {
            driver.findElement(locator);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
        }
    }
}