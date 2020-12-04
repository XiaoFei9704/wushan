package cn.ninanina.wushan.repository;

import cn.ninanina.wushan.domain.TagDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TagRepository extends JpaRepository<TagDetail, Long> {

    List<TagDetail> findByVideoCountGreaterThan(int videoCount);

    @Query(value = "select * from tag order by searchCount desc limit 0, ?1", nativeQuery = true)
    List<TagDetail> findBySearchCountDesc(int count);

    @Query(value = "select min(id) from tag where tagZh is null", nativeQuery = true)
    Long findTranslateWaterMark();

    @Query(value = "select min(id) from tag where start is null", nativeQuery = true)
    Long findPinyinWaterMark();

    @Query(value = "select min(id) from tag where tagZh is not null and indexed is false", nativeQuery = true)
    Long findIndexingWatermark();

    //获取某tag下的部分videoId
    @Query(value = "select video_id from video_tag where tag_id = ?1 limit ?2, ?3", nativeQuery = true)
    List<Long> findVideoIdsForTag(long tagId, int offset, int limit);
}
