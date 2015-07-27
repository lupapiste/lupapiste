LUPAPISTE.VetumaInitModel = function(params) {
  "use strict";

  var self = this;
  var VETUMA_PARAMS = ["success", "error", "cancel", "y", "vtj", "language"];
  var VETUMA_BASE = "/api/vetuma?";

  if (_.isUndefined(params.language)) {
    params.language = loc.currentLanguage;
  }

  function appendParam(url, param) {
    return url + param + "=" + encodeURIComponent(ko.unwrap(params[param])) +  "&";
  }

  self.id = _.isUndefined(params.id) ?  "vetuma-init" : params.id;
  self.visible = _.isUndefined(params.visible) ?  true : params.visible;
  self.href = ko.pureComputed(function() {return _.reduce(VETUMA_PARAMS, appendParam, VETUMA_BASE);});

};
