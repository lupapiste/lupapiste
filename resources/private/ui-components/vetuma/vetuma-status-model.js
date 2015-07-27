LUPAPISTE.VetumaStatusModel = function(params) {
  "use strict";

  var codes = {cancel: params.errorLocPrefix + ".cancel",
               error:  params.errorLocPrefix + ".error",
               y:      params.errorLocPrefix + ".error-y",
               vtj:    params.errorLocPrefix + ".error-vtj"};

  var self = this;
  self.status = params.status;
  self.id = ko.pureComputed(function() {
    return params.idPrefix + self.status();
  });
  self.code = ko.pureComputed(function() {
    return codes[self.status()];
  });

};
