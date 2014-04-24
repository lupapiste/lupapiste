;(function() {
  "use strict";

  var statusModel = new LUPAPISTE.StatusModel();

  hub.onPageChange("link-account", function() {

    var token = pageutil.subPage();

    if (!token) {
      window.location.hash = "#!/register";
      return false;
    }

    var urlPrefix = "/app/" + loc.getCurrentLanguage() + "/welcome";

    $.get("/api/vetuma", {success: urlPrefix + "#!/link-account-2/" + token,
                          cancel:  urlPrefix + "#!/link-account/cancel",
                          error:   urlPrefix + "#!/link-account/error"}, function(d) {
      $("#vetuma-link-account")
        .html(d).find(":submit").addClass("btn btn-primary")
                                .attr("value",loc("register.action"))
                                .attr("id", "vetuma-linking-init");
    });

    statusModel.subPage(pageutil.subPage());

  });

  $(function(){
    $("#link-account").applyBindings(statusModel);
    $("#link-account-2").applyBindings({});
    $("#link-account-3").applyBindings({});
  });
})();