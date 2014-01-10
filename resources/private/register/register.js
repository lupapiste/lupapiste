;(function() {
  "use strict";

  var keys = ['stamp', 'personId', 'firstName', 'lastName', 'email', 'confirmEmail', 'street', 'city', 'zip', 'phone', 'password', 'confirmPassword', 'street', 'zip', 'city'];
  var model;
  var confirmModel = {
    email: ko.observable("")
  };

  function json(model) {
    var d = {};
    for (var i in keys) {
      var key = keys[i];
      d[key] = model[key]() || null;
    }

    delete d.confirmPassword;
    delete d.confirmEmail;
    return d;
  }

  function reset(model) {
    for (var i in keys) {
      if (model[keys[i]] !== undefined) {
      model[keys[i]]('');
        if (model[keys[i]].isModified) {
      model[keys[i]].isModified(false);
    }
      }
    }
    return false;
  }

  function submit(m) {
    var error$ = $('#register-email-error');
    error$.text('');

    ajax.command('register-user', json(m))
      .success(function() {
        confirmModel.email(model().email());
        reset(model());
        window.location.hash = "!/register3";
      })
      .error(function(e) {
        error$.text(loc(e.text));
      })
      .call();
    return false;
  }

  function cancel() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("areyousure"),
      loc("register.confirm-cancel"),
      {title: loc("yes"),
       fn: function() {
        reset(model());
        window.location.hash = "";
      }},
      {title: loc("no")}
    );
  }

  var plainModel = {
    personId: ko.observable(""),
    firstName: ko.observable(""),
    lastName: ko.observable(""),
    stamp: ko.observable(""),
    street: ko.observable("").extend({required: true}),
    city: ko.observable("").extend({required: true}),
    zip: ko.observable("").extend({required: true, number: true, maxLength: 5}),
    phone: ko.observable("").extend({required: true}),
    email: ko.observable("").extend({email: true}),
    password: ko.observable("").extend({validPassword: true}),
    acceptTerms: ko.observable(false),
    disabled: ko.observable(true),
    submit: submit,
    cancel: cancel,
    reset: reset
  };
  plainModel.confirmPassword = ko.observable().extend({equal: plainModel.password});
  plainModel.confirmEmail = ko.observable().extend({equal: plainModel.email});

  function StatusModel() {
    var self = this;
    self.subPage = ko.observable("");
    self.isCancel = ko.computed(function() { return self.subPage() === 'cancel'; });
    self.isError = ko.computed(function() { return self.subPage() === 'error'; });
  }

  var statusModel = new StatusModel();

  model = ko.validatedObservable(plainModel);
  model.isValid.subscribe(function(valid) {
    model().disabled(!valid || !model().acceptTerms());
  });
  model().acceptTerms.subscribe(function() {
    model().disabled(!model.isValid() || !model().acceptTerms());
  });

  function subPage() {
    var hash = (location.hash || "").substr(3);
    var path = hash.split("/");
    var pagePath = path.splice(1, path.length - 1);
    return _.first(pagePath) || undefined;
  }

  hub.onPageChange('register', function() {
    var urlPrefix = "/app/" + loc.getCurrentLanguage() + "/welcome";
    $.get('/api/vetuma', {success: urlPrefix + '#!/register2',
                          cancel:  urlPrefix + '#!/register/cancel',
                          error:   urlPrefix + '#!/register/error'}, function(d) {
      $('#vetuma-register')
        .html(d).find(':submit').addClass('btn btn-primary')
                                .attr('value',loc("register.action"))
                                .attr('id', 'vetuma-init');
    });
    statusModel.subPage(subPage());

  });

  hub.onPageChange('register2', function() {
    reset(model());
    reset(confirmModel);
    ajax.get('/api/vetuma/user')
      .raw(true)
      .success(function(data) {
        if (data) {
          model().personId(data.userid);
          model().firstName(data.firstName);
          model().lastName(data.lastName);
          model().stamp(data.stamp);
          model().city((data.city || ""));
          model().zip((data.zip || ""));
          model().street((data.street || ""));
        } else {
          window.location.hash = "!/register";
        }
      })
      .error(function(e){$('#register-email-error').text(loc(e.text));})
      .call();
  });

  $(function(){
    $('#register').applyBindings(statusModel);
    $('#register2').applyBindings(model);
    $('#register3').applyBindings(confirmModel);
  });

})();
