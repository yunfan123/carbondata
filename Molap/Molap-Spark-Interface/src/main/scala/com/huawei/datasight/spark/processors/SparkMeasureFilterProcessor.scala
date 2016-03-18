/**
  *
  */
package com.huawei.datasight.spark.processors

import com.huawei.unibi.molap.engine.filters.measurefilter.{MeasureFilter, MeasureFilterModel}
import com.huawei.unibi.molap.engine.filters.measurefilter.util.MeasureFilterFactory
import com.huawei.unibi.molap.engine.scanner.impl.{MolapKey, MolapKeyValueGroup, MolapValue}
import com.huawei.unibi.molap.metadata.MolapMetadata.Measure
import org.apache.spark.rdd.RDD

import scala.collection.mutable.MutableList
import scala.util.control.Breaks._

class SparkMeasureFilterProcessor {

  def process(rdd: RDD[(MolapKey, MolapValue)], model: Array[Array[MeasureFilterModel]], dimIndex: Int, queryMsrs: java.util.List[Measure]): RDD[(MolapKey, MolapValue)] = {
    var filters = new Array[Array[MeasureFilter]](model.length)
    filters = MeasureFilterFactory.getMeasureFilter(model, queryMsrs)
    val idxs = getMsrFilterIndexes(filters);
    val len = idxs.length;
    if (dimIndex < 0) {
      val s = rdd.filter { key =>
        var filtered = false
        breakable {
          for (j <- 0 until len) {
            val measureFilter = filters(idxs(j));
            for (k <- 0 until measureFilter.length) {
              if (!(measureFilter(k).filter(key._2.getValues()))) {
                filtered = true
                break
              }
            }
          }
        }
        filtered
      }
      return s
    }
    else {
      val s = rdd.map { key =>
        val value: MolapValue = new MolapKeyValueGroup(key._2.getValues().clone)
        value.addGroup(key._1, key._2)
        (key._1.getSubKey(dimIndex + 1), value) }
        .reduceByKey((v1, v2) => v1.mergeKeyVal(v2))
        .filter { key =>
          var filtered = false
          breakable {
            for (j <- 0 until len) {
              val measureFilter = filters(idxs(j));
              for (k <- 0 until measureFilter.length) {
                if (measureFilter(k).filter(key._2.getValues())) {
                  filtered = true
                  break
                }
              }
            }
          }
          filtered
        }.flatMap { key =>

          val next = key._2.asInstanceOf[MolapKeyValueGroup]
          val list = new MutableList[(MolapKey, MolapValue)]()
          val keys = next.getKeys()
          val values = next.getAllValues();
          for (i <- 0 until keys.size()) {
            list += ((keys.get(i), values.get(i)))
            //             println((keys.get(i),values.get(i)))
          }
          list
        }
      return s
    }
    null
  }

  private def getMsrFilterIndexes(measureFilters: Array[Array[MeasureFilter]]): Array[Int] = {

    val msrIndexes = new MutableList[Int]();

    for (i <- 0 until measureFilters.length) {
      if (measureFilters(i) != null) {
        msrIndexes += i;
      }
    }

    msrIndexes.toArray[Int];
  }

}