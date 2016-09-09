LUPAPISTE.SidePanelService = function() {
  "use strict";
  var self = this;

  self.application = lupapisteApp.models.application;
  self.currentPage = lupapisteApp.models.rootVMO ? lupapisteApp.models.rootVMO.currentPage : ko.observable();
  self.authorization = lupapisteApp.models.applicationAuthModel;

  // Notice
  var latestUrgency = ko.observable({});

  self.urgency = ko.computed({
    read: function() {
      return latestUrgency().id !== self.application.id()
        ? ko.unwrap(self.application.urgency)
        : latestUrgency().urgency;
    },
    write: function( urgency ) {
      latestUrgency( {id: self.application.id(),
                      urgency: urgency});
    }
  });

  self.authorityNotice = ko.pureComputed(function() {
    return ko.unwrap(self.application.authorityNotice);
  });

  self.tags = ko.pureComputed(function() {
    return ko.toJS(self.application.tags);
  });

  var noticeSeenAppId = ko.observable();

  self.unseenNotice = ko.pureComputed( function() {
    return noticeSeenAppId() !== self.application.id()
      && ko.unwrap( self.application.unseenAuthorityNotice);
  });

  hub.subscribe("SidePanelService::NoticeSeen", function() {
    if( self.unseenNotice() ) {
      noticeSeenAppId( self.application.id());
      ajax.command( "mark-seen", {id: self.application.id(),
                                  type: "authority-notices"})
        .call();
    }
  });

  var changeNoticeInfo = _.debounce(function(command, data) {
    ajax
      .command(command, _.assign({id: self.application.id()}, data))
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
    self.urgency( event.urgency );
  });

  hub.subscribe("SidePanelService::AuthorityNoticeChanged", function(event) {
    changeNoticeInfo("add-authority-notice", _.pick(event, "authorityNotice"));
  });

  hub.subscribe("SidePanelService::TagsChanged", function(event) {
    changeNoticeInfo("add-application-tags", _.pick(event, "tags"));
  });

  // Conversation
  var allComments = ko.observableArray([]);
  self.queryComments = function() {
    if (self.authorization.ok("comments")) {
      ajax
        .query("comments", {id: self.application.id()})
        .success(function (res) {
          allComments(res.comments);
        })
        .call();
    }
  };
  hub.subscribe("application-model-updated", self.queryComments);
  hub.subscribe("upload-done", self.queryComments);
  hub.subscribe("attachmentsService::remove", self.queryComments);

  self.showAllComments = ko.observable(true);
  self.mainConversation = ko.observable(true);
  self.target = ko.observable({type: "application"});
  self.authorities = ko.observableArray([]);

  var commentRoles;
  var commentPending = ko.observable();

  ko.computed(function() {
    var state = commentPending() ? "pending" : "finished";
    hub.send("SidePanelService::AddCommentProcessing", {state: state});
  });

  self.comments = ko.pureComputed(function() {
    return _(allComments())
      .filter(
         function(comment) {
           return self.showAllComments()
             || self.target().type === comment.target.type
             && self.target().id   === comment.target.id;
       })
       .reverse()
       .value();
  }).extend({rateLimit: 100});

  // refresh conversation when page changes
  function refresh(pageChange) {
    var page = pageChange.pageId;
    if (page) {
      var type = pageutil.getPage();
      commentRoles = ["applicant", "authority"];
      self.mainConversation(false);
      self.showAllComments(false);
      self.target({type: type, id: pageutil.lastSubPage()});
      switch(type) {
        case "attachment":
        case "statement":
          break;
        case "verdict":
          commentRoles = ["authority"];
          break;
        default:
          self.target({type: "application"});
          self.mainConversation(true);
          self.showAllComments(true);
          break;
      }
    }
  }

  hub.subscribe("page-load", refresh);

  // Fetch authorities/commenters when application changes
  ko.computed(function() {
    var applicationId = ko.unwrap(self.application.id);
    if (applicationId && self.authorization.ok("application-commenters") ) {
      ajax.query("application-commenters", {id: applicationId})
      .success(function(resp) {
        self.authorities(resp.authorities);
      })
      .call();
    }
  }).extend({throttle: 100});

  hub.subscribe("SidePanelService::UnseenCommentsSeen", function() {
    // Mark comments seen after a second
    if (self.application.unseenComments()) {
      setTimeout(function() {
        if (self.application.id() && self.authorization.ok("mark-seen")) {
          ajax.command("mark-seen", {id: self.application.id(), type: "comments"})
          .success(function() {
            self.application.unseenComments(0);
          })
          .error(_.noop)
          .call();
        }
      }, 1000);
    }
  });

  hub.subscribe("SidePanelService::AddComment", function(event) {
    var markAnswered = Boolean(event.markAnswered);
    var openApplication = Boolean(event.openApplication);
    var text = event.text || "";
    var to = event.to;
    ajax.command("add-comment", {
        id: ko.unwrap(self.application.id),
        text: _.trim(text),
        target: self.target(),
        to: to,
        roles: commentRoles,
        "mark-answered": markAnswered,
        openApplication: openApplication
    })
    .pending(commentPending)
    .success(function() {
      hub.send("SidePanelService::AddCommentProcessed", {status: "success"});
      if (markAnswered) {
        hub.send("show-dialog", {ltitle: "comment-request-mark-answered-label",
                                 component: "ok-dialog",
                                 size: "small",
                                 componentParams: {ltext: "comment-request-mark-answered.ok"}});
      }
      repository.load(ko.unwrap(self.application.id)); // TODO: use comments query instead ?
    })
    .call();
  });
};
