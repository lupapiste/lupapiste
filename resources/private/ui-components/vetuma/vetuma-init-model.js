LUPAPISTE.VetumaInitModel = function(params) {
  "use strict";

  var self = this;
  var VETUMA_PARAMS = ["success", "error", "cancel", "y", "vtj", "language"];
  var VETUMA_BASE = features.enabled("dummy-ident") ? "/dev/saml-login?" :
                    (features.enabled("suomifi-ident") ? "/api/saml/login?" : "/api/vetuma?");

  if (_.isUndefined(params.language)) {
    params.language = loc.currentLanguage;
  }

  self.id = _.isUndefined(params.id) ?  "vetuma-init" : params.id;
  self.visible = _.isUndefined(params.visible) ?  true : params.visible;
  self.href = ko.pureComputed(function() {
    var getParams = _.map(VETUMA_PARAMS, function(param) {
      return param + "=" + encodeURIComponent(ko.unwrap(params[param]));
    }).join("&");
    return VETUMA_BASE + getParams;
  });
};
