LUPAPISTE.SidePanelService = function() {
  "use strict";
  var self = this;

  var application = lupapisteApp.models.application;

  self.currentPage = lupapisteApp.models.rootVMO ? lupapisteApp.models.rootVMO.currentPage : ko.observable();

  self.authorization = lupapisteApp.models.applicationAuthModel;

  // Notice
  self.urgency = ko.computed(function() {
    return ko.unwrap(application.urgency);
  });

  self.authorityNotice = ko.computed(function() {
    return ko.unwrap(application.authorityNotice);
  });

  self.tags = ko.computed(function() {
    return ko.toJS(application.tags);
  });

  var changeNoticeInfo = _.debounce(function(command, data) {
    ajax
      .command(command, _.assign({id: application.id()}, data))
      .success(function() {
        hub.send("SidePanelService::NoticeChangeProcessed", {status: "success"});
      })
      .error(function() {
        hub.send("SidePanelService::NoticeChangeProcessed", {status: "failed"});
      })
      .call();
  }, 500);

  hub.subscribe("SidePanelService::UrgencyChanged", function(event) {
    changeNoticeInfo("change-urgency", _.pick(event, "urgency"));
  });

  hub.subscribe("SidePanelService::AuthorityNoticeChanged", function(event) {
    changeNoticeInfo("add-authority-notice", _.pick(event, "authorityNotice"));
  });

  hub.subscribe("SidePanelService::TagsChanged", function(event) {
    changeNoticeInfo("add-application-tags", _.pick(event, "tags"));
  });

  // Conversation
  self.comments = ko.observableArray([]);

  self.showAllComments = ko.observable(true);
  self.mainConversation = ko.observable(true);
  self.target = ko.observable({type: "application"});

  self.comments = ko.computed(function() {
    var filteredComments =
      _.filter(ko.mapping.toJS(application.comments),
        function(comment) {
          return self.showAllComments() || self.target().type === comment.target.type && self.target().id === comment.target.id;
        });
    return filteredComments;
  });

  // refresh conversation
  ko.computed(function() {
    var page = self.currentPage();
    if (page) {
      var type = pageutil.getPage();
      switch(type) {
        case "attachment":
        case "statement":
          self.mainConversation(false);
          self.showAllComments(false);
          self.target({type: type, id: pageutil.lastSubPage()});
          break;
        case "verdict":
          self.mainConversation(false);
          self.showAllComments(false);
          self.target({type: type, id: pageutil.lastSubPage()}, ["authority"]);
          break;
        default:
          self.mainConversation(true);
          self.showAllComments(true);
          break;
      }
    }
  });

  self.authorities = ko.observableArray([]);

  // Fetch authorities when application changes
  ko.computed(function() {
    var applicationId = ko.unwrap(application.id);
    if (applicationId && self.authorization.ok("application-authorities") ) {
      ajax.query("application-authorities", {id: applicationId})
      .success(function(resp) {
        self.authorities(resp.authorities);
      })
      .call();
    }
  });

};
