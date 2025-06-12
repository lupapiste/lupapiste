(function($) {
  "use strict";

  var PW_CHANGED_DIALOG_ID = "password-changed-dialog";

  function Reset() {
    var self = this;
    self.email = ko.observable();
    self.ok = ko.computed(function() { var v = self.email(); return v && v.length > 0; });
    self.sent = ko.observable(false);
    self.fail = ko.observable();
    self.sentNote = ko.observable();
    self.send = function() {
      var email = _.toLower( _.trim( self.email() ));
      if (!_.isBlank(email)) {
        ajax
          .postJson("/api/reset-password", {"email": email})
          .raw(false)
          .success(function() {
            self.sent(true).fail(null).email("");
            self.sentNote( loc( "a11y.password-reset.email-sent", email));
          })
          .error(function(e) { self.sent(false).fail(e.text); $("#reset input:first").focus(); })
          .call();
      }
    };
  }

  function SetPW() {
    var self = this;
    ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

    self.token = ko.observable();
    self.password1 = ko.observable().extend({validPasswordLength: true});
    self.password2 = ko.observable().extend({required: true, equal: self.password1});
    self.passwordQuality = ko.observable("");
    self.disposedSubscribe(self.password1, function (pw) { util.getPwQualityAsync(pw, self.passwordQuality, {"token": self.token()});});
    self.ok = ko.pureComputed(function() {
      var t = self.token(),
          p1 = self.password1(),
          p2 = self.password2();
      return t && t.length && p1 &&
        util.isValidPasswordLength(p1) &&
        util.isValidPasswordQuality(self.passwordQuality()) &&
        p1 === p2;
    });
    self.fail = ko.observable(false);
    self.showPasswords = ko.observable(false);

    self.send = function() {
      ajax
        .post("/api/token/" + self.token())
        .json({password: self.password1()})
        .success(function() {
          self.fail(false).password1("").password2("");

          hub.send("show-dialog",
              {id: PW_CHANGED_DIALOG_ID,
               ltitle: "success.dialog.title",
               size: "medium",
               component: "ok-dialog",
               componentParams: {ltext: "setpw.success", okTitle: loc("welcome.login")}});
        })
        .fail(function() { self.fail(true).password1("").password2(""); $("#setpw input:first").focus(); })
        .call();
    };

    hub.onPageLoad("setpw", function(e) {
      self.token(e.pagePath[0]);

      var newUrl = location.pathname + location.hash.replace(/\/[^/]*$/, "");
      history.replaceState(null, null, newUrl);
    });
  }

  hub.subscribe({eventType: "dialog-close", id: PW_CHANGED_DIALOG_ID}, pageutil.openFrontpage);

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
