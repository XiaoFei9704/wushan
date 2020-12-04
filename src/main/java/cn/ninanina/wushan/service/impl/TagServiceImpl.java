package cn.ninanina.wushan.service.impl;

import cn.ninanina.wushan.common.util.CommonUtil;
import cn.ninanina.wushan.common.util.LuceneUtil;
import cn.ninanina.wushan.domain.TagDetail;
import cn.ninanina.wushan.domain.VideoDetail;
import cn.ninanina.wushan.repository.TagRepository;
import cn.ninanina.wushan.repository.VideoRepository;
import cn.ninanina.wushan.service.TagService;
import cn.ninanina.wushan.service.cache.TagCacheManager;
import cn.ninanina.wushan.service.cache.VideoCacheManager;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Version;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
@Slf4j
public class TagServiceImpl implements TagService {
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private VideoRepository videoRepository;
    @Autowired
    private TagCacheManager tagCacheManager;
    @Autowired
    private VideoCacheManager videoCacheManager;

    @Override
    public List<TagDetail> hotTags(int count) {
        return tagRepository.findBySearchCountDesc(count);
    }

    @Override
    public long tagCount() {
        return tagRepository.count();
    }

    @Override
    public List<VideoDetail> getVideosForTag(TagDetail tag, int offset, int limit, String sort) {
        List<VideoDetail> result = new ArrayList<>();
        tag.setSearchCount(tag.getSearchCount() + 1);
        tagRepository.save(tag);
        List<Long> videoIds = tagCacheManager.getVideoIdsOfTag(tag);

        if (sort.equals("random")) {
            if (videoIds != null) {
                for (long id : videoIds) {
                    if (--offset >= 0) continue;
                    if (--limit < 0) break;
                    result.add(videoRepository.getOne(id));
                }
            } else {
                videoIds = tagRepository.findVideoIdsForTag(tag.getId(), offset, limit);
                for (long id : videoIds) {
                    videoRepository.findById(id).ifPresent(result::add);
                }
            }
            Collections.shuffle(result);
            return result;
        } else {
            if (videoIds != null) {
                videoIds = videoRepository.findLimitedByIdsWithOrder(videoIds, sort, offset, limit);
                for (long id : videoIds) {
                    videoRepository.findById(id).ifPresent(result::add);
                }
            } else {
                result.addAll(videoRepository.findLimitedWithOrder(tag.getId(), sort, offset, limit));
            }
        }
        Collections.shuffle(result);
        return result;
    }

    @Override
    public List<TagDetail> getTagsStartWith(char c, int page, int size) {
        TagDetail tagDetail = new TagDetail();
        tagDetail.setStart(c);
        Page<TagDetail> tagDetails = tagRepository.findAll(Example.of(tagDetail),
                PageRequest.of(page, size, Sort.by(new Sort.Order(Sort.Direction.DESC, "videoCount"))));
        List<TagDetail> result = tagDetails.getContent();
        for (TagDetail tag : result) {
            if (StringUtils.isEmpty(tag.getCover())) {
                String cover = videoRepository.findCoverForTag(tag.getId());
                tag.setCover(cover);
                tagRepository.save(tag);
            }
        }
        return result;
    }

    @SneakyThrows
    @Override
    public List<TagDetail> suggestTag(@Nonnull String word) {
        List<TagDetail> result = new ArrayList<>();
        String pinyin = CommonUtil.getPinyin(word);
        PrefixQuery prefixQuery = new PrefixQuery(new Term("pinyin", pinyin));
        IndexSearcher indexSearcher = LuceneUtil.get().getTagIndexSearcher();
        TopDocs topDocs = indexSearcher.search(prefixQuery, 50);
        for (int i = 0; i < 50 && i < topDocs.scoreDocs.length; i++) {
            ScoreDoc scoreDoc = topDocs.scoreDocs[i];
            Document document = indexSearcher.doc(scoreDoc.doc);
            long id = Long.parseLong(document.get("id"));
            tagRepository.findById(id).ifPresent(result::add);
        }
        result.sort((o1, o2) -> o2.getVideoCount() - o1.getVideoCount());
        for (TagDetail tag : result) {
            if (StringUtils.isEmpty(tag.getCover())) {
                String cover = videoRepository.findCoverForTag(tag.getId());
                tag.setCover(cover);
                tagRepository.save(tag);
            }
        }
        if (result.size() > 10) return result.subList(0, 10);
        else return result;
    }

    @SneakyThrows
    @Override
    public List<TagDetail> searchForTag(@Nonnull String word, @Nonnull Integer offset, @Nonnull Integer limit) {
        List<TagDetail> result = new ArrayList<>();
        IndexSearcher indexSearcher = LuceneUtil.get().getTagIndexSearcher();
        String[] fields = {"tag", "tagZh", "pinyin"};
        QueryParser parser = new MultiFieldQueryParser(Version.LUCENE_4_10_4, fields, LuceneUtil.get().getAnalyzer());
        Query query = parser.parse(word);
        TopDocs topDocs = indexSearcher.search(query, offset + limit);
        for (int i = offset; i < offset + limit && i < topDocs.scoreDocs.length; i++) {
            ScoreDoc scoreDoc = topDocs.scoreDocs[i];
            Document document = indexSearcher.doc(scoreDoc.doc);
            long id = Long.parseLong(document.get("id"));
            tagRepository.findById(id).ifPresent(result::add);
        }
        return result;
    }
}
