vetuma = function(e$,success,urls) {
  "use strict";
  ajax
    .get('/api/vetuma/user')
    .raw(true)
    .success(function(user) {
      if(user) {
        if(success) {
          success(user);
        } else {
          debug("vetuma successful. no callback registered.");
        }
      } else {
        if(!urls) {
          var url = window.location.pathname + window.location.search + window.location.hash;
          var urlmap = urls ? urls : {success: url, cancel: url, error: url};
          $.get('/api/vetuma', urlmap, function(form) {
            e$.html(form).find(':submit').addClass('btn btn-primary')
                                         .attr('value',loc("register.action"))
                                         .attr('data-test-id', 'vetuma-init');
          });
        }
      }
    })
    .call();
};
