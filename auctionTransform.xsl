<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE stylesheet [
  <!ENTITY space "<xsl:text> </xsl:text>">
  <!ENTITY bidColor "">
]>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:jxf="com.jbidwatcher.auction.AuctionTransformer">
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
        <script>
          function resetRows() {
            toggle = 0;
            rows=document.getElementsByTagName('tr');
            for(i in rows) {
              row=rows[i];
              if(row) {
                if(row.style) row.style.display='table-row';
                if(row.className &amp;&amp; row.className != '') {
                  do_toggle(Math.floor(toggle / 2), row);
                  toggle = (toggle +1) % 4;
                }
              }
            }
          }

          function do_toggle(toggle, row) {
            row.className = row.className.replace('row0 ', '').replace('row1 ', '');
            row.className = 'row' + toggle + ' ' + row.className;
          }

          function onlyRows(classname) {
            toggle = 2;
            rows=document.getElementsByTagName('tr');
            for(i in rows) {
              row=rows[i];
              if(row.className &amp;&amp; row.className != '') {
                if(!row.className.match(classname)) {
                  row.style.display='none';
                } else {
                  do_toggle(Math.floor(toggle / 2), row);
                  toggle = (toggle +1) % 4;
                }
              }
            }
          };

          function categories() {
            cats = new Array();
            rows = document.getElementsByTagName('tr');
            for(i in rows) {
              row = rows[i];

              if(row.className &amp;&amp; row.className != '') {
                classes = row.className.split(' ');
                for(index in classes) {
                  if(!classes[index].match('^row[0-9]') &amp;&amp; cats[classes[index]] != 1) {
                    cats[classes[index]] = 1;
                  }
                }
              }
            }
            return cats;
          };

          function fillSelect() {
            c = categories();
            select = document.getElementById('tabselect');
            for(i in c) add_select_val(select, i);
          };

          function add_select_val(select_elm, val){
            var option = document.createElement('option');
            option.setAttribute('value', val);
            option.innerHTML = val;
            select_elm.appendChild(option);
          };
        </script>
      </head>
      <body>
        <div style="padding-bottom: 3px;">
          <form name="Add Auction" action="./addAuction" method="GET" style="display: inline;">
            <label for="id">Auction Id:</label><input name="id" size="20" value=""/><input type="submit" name="action" value="Add Auction" onClick=""/>
          </form> | 
          <form style="display: inline;">Select Tab to View: <select name="tab" id="tabselect" onchange="resetRows(); if(this.value != 'all') onlyRows(this.value);"><option value="all">All</option></select></form>
        </div>
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
            <xsl:variable name="category"><xsl:value-of select="category"/></xsl:variable>
            <tr class="row{$rowclass} {$category}">
              <td width="100%" colspan="8">
                <font size="3"><strong><a href="/{$curid}"><xsl:value-of select="info/title"/></a></strong></font>
              </td>
            </tr>
            <tr class="row{$rowclass} {$category}" style="font-size: small">
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
                  <xsl:when test="snipe">
                    Snipe: <xsl:value-of select="snipe/@quantity"/> @ <xsl:value-of select="snipe/@currency"/>&space;<xsl:value-of select="snipe/@price"/>
                  </xsl:when>
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
        <script type="text/javascript">fillSelect();</script>
      </body>
    </html>
  </xsl:template>
</xsl:stylesheet>
