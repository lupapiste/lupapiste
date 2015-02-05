(function(i,s,o,g,r,a,m) {
  "use strict";
  if (LUPAPISTE.config.analytics.id) {
    i.GoogleAnalyticsObject=r;
    i[r]=i[r]||function(){(i[r].q=i[r].q||[]).push(arguments);};
    i[r].l=1*new Date();
    a=s.createElement(o);
    m=(s.getElementsByTagName(o)[0]);
    a.async=1;
    a.src=g;
    m.parentNode.insertBefore(a,m);

    var gaConfig = window.location.hostname === "localhost" ? {"cookieDomain": "none"}: "auto";
    ga("create", LUPAPISTE.config.analytics.id, gaConfig);
  } else {
    window.ga = util.nop;
  }

  hub.subscribe("page-load", function(e) {
    var nonIdParts = _.filter(e.pagePath, function(s) {
      return !s.match(/^([a-z0-9]{24}|[a-zA-Z0-9]{48}|LP-\d{3}-\d{4}-\d{5})$/);
    });
    var page = ["", e.pageId].concat(nonIdParts).join("/");
    ga("send", "pageview", page);
  });

})(window,document,"script","//www.google-analytics.com/analytics.js","ga");
