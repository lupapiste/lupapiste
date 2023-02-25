LUPAPISTE.SidePanelService = function() {
  "use strict";
  var self = this;

  self.application = lupapisteApp.models.application;
  self.currentPage = lupapisteApp.models.rootVMO ? lupapisteApp.models.rootVMO.currentPage : ko.observable();
  self.authorization = lupapisteApp.models.applicationAuthModel;

  // Notice
  // Observables will have objects with id and value.
  var noticeLatest = {urgency: ko.observable({}),
                      authorityNotice: ko.observable({})};
  function latestNotice( field ) {
    return ko.computed({
      read: function() {
        var latest = noticeLatest[field]();
        return latest.id !== self.application.id()
          ? ko.mapping.toJS(self.application[field])
          : latest.value;
      },
      write: function( value ) {
        noticeLatest[field]( {id: self.application.id(),
                              value: value});
      }
    });
  }

  self.urgency = latestNotice( "urgency");
  self.authorityNotice = latestNotice( "authorityNotice");

  var tagsService = lupapisteApp.services.organizationTagsService;
  self.tags = ko.computed( {
    read: function() {
      var tagIds = self.application.tags();
      return _.filter( tagsService.currentApplicationOrganizationTags(),
                       function( tag ) {
                         return _.includes( tagIds, tag.id );
                       });
    },
    write: function( tagIds ) {
      self.application.tags( tagIds );
    }
  });

  var noticeSeenAppId = ko.observable();

  self.unseenNotice = ko.pureComputed( function() {
    return noticeSeenAppId() !== self.application.id()
      && ko.unwrap( self.application.unseenAuthorityNotice);
  });


  var companyNotes = ko.pureComputed(function() {
    return _.merge({tags: ko.observable(), note: ko.observable()},
                   _.find(ko.unwrap(self.application["company-notes"]), function(item) {
                     return ko.unwrap(item.companyId) === util.getIn(lupapisteApp.models.currentUser, ["company", "id"]);
                   }));
  });

  self.companyNote = ko.pureComputed({
    read: function() { return util.getIn(companyNotes,["note"]); },
    write: function(value) { companyNotes().note(value); }
  });

  self.companyTags = ko.pureComputed({
    read: function() { return util.getIn(companyNotes,["tags"]); },
    write: function(value) { companyNotes().tags(value); }
  });

  var companyNoteSeenAppId = ko.observable();

  self.unseenCompanyNote = ko.pureComputed( function() {
    return companyNoteSeenAppId() !== self.application.id()
      && ko.unwrap( self.application.unseenCompanyNote);
  });

  hub.subscribe("SidePanelService::NoticeSeen", function() {
    noticeSeenAppId(pageutil.hashApplicationId());
    if( self.unseenNotice() ) {
      ajax.command( "mark-seen", {id: self.application.id(),
                                  type: "authority-notices"})
        .call();
    }
  });

  function clearNotice() {
    noticeLatest.urgency({} );
    noticeLatest.authorityNotice( {} );
    noticeSeenAppId("");
  }

  hub.subscribe( "application-model-updated", clearNotice );

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
    self.authorityNotice( event.authorityNotice );
  });

  hub.subscribe("SidePanelService::TagsChanged", function(event) {
    changeNoticeInfo("add-application-tags", _.pick(event, "tags"));
    self.tags( event.tags );
  });

  hub.subscribe("SidePanelService::CompanyNoteChanged", function(event) {
    changeNoticeInfo("update-application-company-notes", _.pick(event, "note"));
    self.companyNote( event.note );
  });

  hub.subscribe("SidePanelService::CompanyTagsChanged", function(event) {
    changeNoticeInfo("update-application-company-notes", _.pick(event, "tags"));
    self.companyTags( event.tags );
  });

  // Conversation
  var allComments = lupapisteApp.services.commentService.comments;

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
    self.application.authorityNotice = ko.observable();
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
      to: _.isBlank( to ) ? null : _.trim( to ),
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

  // Navi bar support (moved from the side panel model to service)

  self.showSidePanel = ko.observable(false);
  self.enableSidePanel = ko.observable(false);

  self.showConversationPanel = ko.observable(false);
  self.showNoticePanel = ko.observable(false);
  self.showCompanyNotesPanel = ko.observable(false);
  self.showInfoPanel = ko.observable( false );
  self.showStar = lupapisteApp.services.infoService.showStar;

};
