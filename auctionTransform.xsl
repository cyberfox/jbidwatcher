<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE stylesheet [
  <!ENTITY space "<xsl:text> </xsl:text>">
  <!ENTITY bidColor "">
]>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:jxf="http://xml.apache.org/xalan/java/com.jbidwatcher.xml.JTransformer">
  <xsl:output method="html" indent="yes" doctype-public="-//W3C//DTD HTML 4.0 Transitional//EN"/>
  <xsl:template name="textify" match="/">
    <xsl:for-each select="jbidwatcher/auctions/server/auction">
    Title: <b><xsl:value-of select="./info/title"/></b>
    </xsl:for-each>
  </xsl:template>
  <xsl:template match="/">
    <html>
      <head>
        <title>Auction List</title>
        <style>
          .winner { color: green }
          .notwinner { color: red }
          .unbid { color: black }
          .row0 { background-color: #CFCFCF }
          .row1 { background-color: #EFEFEF }
          td { border-style: hidden; }
          th { font-size: small; }
        </style>
      </head>
      <body>
        <form name="Add Auction" action="./addAuction" method="GET">
          <table border="0">
            <tr><td>Auction Id:</td><td><input name="id" size="20" value=""/></td><td><input type="submit" name="action" value="Add Auction" onClick=""/></td></tr>
          </table>
        </form>
        <table border="1" cellpadding="0" cellspacing="0" width="100%" bgcolor="#CCCCFF">
          <tr>
            <font size="2">
              <th align="center" width="10%">Item</th>
              <th align="center" width="10%">Start Price</th>
              <th align="center" width="11%">Current Price</th>
              <th align="center" width="11%">My Max/Snipe Bid</th>
              <th align="center" width="8%"># of Bids</th>
              <th align="center" width="11%">Start Date</th>
              <th align="center" width="18%"><strong>End Date PDT</strong></th>
              <th align="center" width="9%">Time Left</th>
            </font>
          </tr>
          <xsl:for-each select="jbidwatcher/auctions/server/auction">
            <xsl:variable name="rowclass"><xsl:value-of select="position() mod 2"/></xsl:variable>
            <xsl:variable name="curid"><xsl:value-of select="@id"/></xsl:variable>
            <tr class="row{$rowclass}">
              <td width="100%" colspan="8">
                <font size="3"><strong><a href="/{$curid}"><xsl:value-of select="info/title"/></a></strong></font>
              </td>
            </tr>
            <tr class="row{$rowclass}" style="font-size: small">
              <xsl:variable name="item_class">
              <xsl:choose>
                <xsl:when test="info/highbidder = ../../server/@user">winner</xsl:when>
                <xsl:when test='info/bidcount = 0'>unbid</xsl:when>
                <xsl:otherwise>notwinner</xsl:otherwise>
              </xsl:choose></xsl:variable>
              <td width="10%" align="center" class="{$item_class}"><xsl:value-of select="@id"/></td>
              <td width="10%" align="right" class="{$item_class}"><xsl:value-of select="info/minimum/@currency"/>&space;<xsl:value-of select="info/minimum/@price"/></td>
              <td width="11%" align="right" class="{$item_class}"><xsl:value-of select="info/currently/@currency"/>&space;<xsl:value-of select="info/currently/@price"/></td>
              <td width="11%" align="right" class="{$item_class}">
                <xsl:choose>
                  <xsl:when test="bid">
                    <xsl:value-of select="bid/@quantity"/> @ <xsl:value-of select="bid/@currency"/>&space;<xsl:value-of select="bid/@price"/>
                  </xsl:when>
                  <xsl:otherwise>--</xsl:otherwise>
                </xsl:choose>
              </td>
              <td width="8%" align="center" class="{$item_class}"><xsl:choose><xsl:when test="info/fixed">(FP)</xsl:when><xsl:otherwise><xsl:value-of select="info/bidcount"/></xsl:otherwise></xsl:choose></td>
              <td width="11%" align="center" class="{$item_class}">
                <xsl:variable name="curdate"><xsl:value-of select="info/start"/></xsl:variable>
                <xsl:value-of select="jxf:formatDate($curdate)"/>
              </td>
              <td width="18%" align="center" class="{$item_class}">
                <xsl:variable name="curdate"><xsl:value-of select="info/end"/></xsl:variable>
                <xsl:value-of select="jxf:formatDate($curdate)"/>
              </td>
              <td width="9%" align="center" class="{$item_class}">
                <xsl:choose>
                  <xsl:when test="complete">Auction Ended</xsl:when>
                  <xsl:otherwise>
                    <xsl:variable name="thisid"><xsl:value-of select="@id"/></xsl:variable>
                    <a href="snipe?id={$thisid}"><xsl:value-of select="jxf:getTimeLeft($thisid)"/></a>
                  </xsl:otherwise>
                </xsl:choose>
              </td>
              <xsl:text>
              </xsl:text>
            </tr>
          </xsl:for-each>
        </table>
      </body>
    </html>
  </xsl:template>
</xsl:stylesheet>
