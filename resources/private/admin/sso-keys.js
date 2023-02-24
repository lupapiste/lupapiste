;(function() {
  "use strict";

  function SsoKeysModel() {
    var self = this;

    self.ssoKeys = ko.observableArray();
    self.newRow = {
      ip: ko.observable(),
      key: ko.observable(),
      comment: ko.observable()
    };

    self.load = function() {
      ajax
        .query("get-single-sign-on-keys")
        .success(function(d) {
          var completed = _.map(d.ssoKeys, function(ssoKey) {
            return _.assign({ip: "", key: "", comment: ""}, ssoKey);
          });
          ko.mapping.fromJS(completed, {}, self.ssoKeys);
        })
        .call();
    };

    self.add = function() {
      var ssoKeyJS = ko.mapping.toJS(self.newRow);
      ajax
        .command("add-single-sign-on-key", ssoKeyJS)
        .success(function(d) {
          self.ssoKeys.push(ko.mapping.fromJS(_.assign(ssoKeyJS, {id: d.id, key: ""})));
          _.forEach(self.newRow, function(value) {
            value("");
          });
          hub.send("sso-keys-changed");
        })
        .call();
    };

    self.update = function(ssoKey) {
      ajax
        .command("update-single-sign-on-key", {
          "sso-id": ssoKey.id().toString(),
          "ip": ssoKey.ip(),
          "key": ssoKey.key(),
          "comment": ssoKey.comment()
        })
       .success(function(resp){
         util.showSavedIndicator(resp);
         hub.send("sso-keys-changed");
       })
        .call();
    };

    self.remove = function(ssoKey) {
      var removeFn = function () {
        ajax
          .command("remove-single-sign-on-key", {"sso-id": ssoKey.id()})
          .success(function() {
            self.ssoKeys.remove(ssoKey);
            hub.send("sso-keys-changed");
          })
          .call();
      };
      hub.send("show-dialog", {title: "Poista SSO-avain",
                               size: "medium",
                               component: "yes-no-dialog",
                               componentParams: {text: "Haluatko varmasti poistaa SSO-avaimen",
                                                 yesFn: removeFn}});
    };
  }

  var ssoKeysModel = new SsoKeysModel();

  hub.onPageLoad("single-sign-on-keys", ssoKeysModel.load);

  $(function() {
    $("#single-sign-on-keys").applyBindings(ssoKeysModel);
  });

})();
