(function($) {
  "use strict";

  function Reset() {
    var self = this;
    self.email = ko.observable();
    self.ok = ko.computed(function() { var v = self.email(); return v && v.length > 0; });
    self.sent = ko.observable(false);
    self.fail = ko.observable();
    self.send = function() {
      var email = self.email();
      if (!_.isBlank(email)) {
        ajax
          .postJson("/api/reset-password", {"email": email})
          .raw(false)
          .success(function() { self.sent(true).fail(null).email(""); })
          .error(function(e) { self.sent(false).fail(e.text); $("#reset input:first").focus(); })
          .call();
      }
    };
  }

  function SetPW() {
    var self = this;

    self.token = ko.observable();
    self.password1 = ko.observable();
    self.password2 = ko.observable();
    self.passwordQuality = ko.pureComputed(function() { return util.getPwQuality(self.password1()); });
    self.ok = ko.pureComputed(function() {
      var t = self.token(),
          p1 = self.password1(),
          p2 = self.password2();
      return t && t.length && p1 && p1.length >= LUPAPISTE.config.passwordMinLength && p1 === p2;
    });
    self.fail = ko.observable(false);

    var successOkButton = {title: loc("welcome.login"),
        fn: function() {
          LUPAPISTE.ModalDialog.close();
          pageutil.openFrontpage();}};

    self.send = function() {
      ajax
        .post("/api/token/" + self.token())
        .json({password: self.password1()})
        .success(function() {
          self.fail(false).password1("").password2("");


          LUPAPISTE.ModalDialog.showDynamicOk(loc("success.dialog.title"), loc("setpw.success"), successOkButton);
        })
        .fail(function() { self.fail(true).password1("").password2(""); $("#setpw input:first").focus(); })
        .call();
    };

    hub.onPageLoad("setpw", function(e) { self.token(e.pagePath[0]);});

  }

  //
  // Initialize:
  //

  var resetModel = new Reset();
  var pwInputModel = new SetPW();

  $(function() {
    if (document.getElementById("reset")) {
      $("#reset").applyBindings(resetModel);
    }
    if (document.getElementById("setpw")) {
      $("#setpw").applyBindings(pwInputModel);
    }
  });

})(jQuery);
