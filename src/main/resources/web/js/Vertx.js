console.log("strt vertx");

//eb = new vertx.EventBus(window.location.protocol + '//' + window.location.hostname + ':' + window.location.port + '/eventbus');



function authenticate(phn, pword)
{
	
	
	console.log("authenticating.... : " + phn+ pword);
	
	//if(phn != '' && pword != '')
	if(phn !=  '')
	{
		eb.send('chat.auth',
		{
			'phone' : phn,
			'pass' : pword
		}, function(reply)
		{
			
			console.log(reply.stat);
			console.log(reply.token);
			if(reply.stat === 'ok')
			{

				TOKEN = reply.token;
				UID = reply.uid;
				SELF_NUM = phn;
				console.log("You logged in as " + phn + " and a password of " + pword);
				setCookie("phone", SELF_NUM, 1);
				setCookie("token", TOKEN, 1);
				console.log("setCookie");
				
				register(TOKEN);
				
				
				loadAccount(sampleConversations,sampleContacts);
				/*
				loggedIn = true;
				setCookie("phone", phn, 1);
				setCookie("token", TOKEN, 1);
				console.log("phone: " + phn);
				console.log("token: " + TOKEN);
				chatDisplay(phn);
				*/
				
			}
			else
			{
				console.log('invalid login');
				alert('invalid login');
			}
		});
	}
	else
		{
		alert('invalid number');
		}
	return ;
}

function verify(phn, token)
{
	var ret = false;
	
	console.log("verifying.... : " + phn + token);
	
//	register(token);
//	return true;
	
	//if(phn != '' && pword != '')
	if(phn !=  '')
	{
		eb.send('chat.auth',
		({
			'phone' : phn,
			'token' : token,
		}), function(reply)
		{
			
			console.log(reply.stat);
			if(reply.stat === 'ok')
			{

				TOKEN = token;
				SELF_NUM = phn;
				//UID = reply.uid;
				console.log("You logged in as " + phn + " and a token of " + token);
				register(token);
				
				loadAccount(sampleConversations,sampleContacts);
				
				
				return false;
				
			}
			else
			{
				console.log("invalid token");
				loadLogin();
				return false;
				
			}
		});
	}
	else
	{
	alert('invalid number');
	}
	return false;
}

function publish(packet,token)
{
	console.log(packet);
	console.log("is being sent");
	if (eb)
	{
		eb.publish('in.cm',packet);
	}
}

function register(t)
{
	var address = "out.cm." + t;
	console.log(address);
	eb.registerHandler(address, function(msg, replyTo)
	{
		console.log("caught it!!");
		console.log(msg);
		// var sender= msg.get('f');
		// var data= msg.get('d');
		receiveMessage(msg); // /Need to be created

	});
}

function initializeBus()
{
	console.log("Initializing eventBus......");
	eb = new vertx.EventBus(window.location.protocol + '//' + window.location.hostname + ':' + window.location.port + '/eventbus');
	
	var timer = null;
	
	eb.onopen = function()
	{
		console.log("eventBus is now open" + eb.readyState());
		//checkCreds();
		checkCookie();
		ebFlag = true;
		
	};
	
	eb.onclose = function()
	{
		console.log("eventBus is now close.....attempting to reconnect in 5 seconds... " + eb.readyState());
		timer=setTimeout(function(){initializeBus()}, 5 * 1000 );
		ebFlag = false;
		eb = null;
	};
	
}

console.log("end vertx");

