LUPAPISTE.AuthorityNoticeModel = function(params) {
  "use strict";
  var self = this;

  self.notice = LUPAPISTE.NoticeModel ? new LUPAPISTE.NoticeModel() : {};

  self.application = params.application;

  ko.computed(function() {
    self.application.id();
    if (self.notice.refresh) {
      self.notice.refresh(self.application);
    }
  });
};
