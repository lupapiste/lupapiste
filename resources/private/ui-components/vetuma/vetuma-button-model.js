/**
 * Parameter model that can be used with LUPAPISTE.VetumaInitModel
 */
LUPAPISTE.VetumaButtonModel = function(urlPrefix, buttonId, step1page, step2page) {
  "use strict";
  var self = this;

  self.id = buttonId;

  self.token = ko.observable();

  self.success = ko.pureComputed(function() {
    return urlPrefix + "#!/" + step2page + "/" + self.token();
  });
  self.cancel = ko.pureComputed(function() {
    return urlPrefix + "#!/" + step1page + "/" + self.token() + "/cancel";
  });
  self.error = ko.pureComputed(function() {
    return urlPrefix + "#!/" + step1page + "/" + self.token() + "/error";
  });
  self.y = ko.pureComputed(function() {
    return urlPrefix + "#!/" + step1page + "/" + self.token() + "/y";
  });
  self.vtj = ko.pureComputed(function() {
    return urlPrefix + "#!/" + step1page + "/" + self.token() + "/vtj";
  });

  self.visible = ko.observable(false);
};
