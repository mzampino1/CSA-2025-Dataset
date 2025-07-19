Ruby
class MyApp < Sinatra::Base
    enable :sessions
  
    get '/' do
        erb :index
    end
  
    get '/login' do
        erb :login_form
    end
  
    post '/login' do
        if params[:username] == 'admin' && params[:password] == 'admin'
            session[:user_id] = 1
            redirect '/'
        else
            @error = 'Invalid username or password'
            erb :login_form
        end
    end
  
    get '/logout' do
        session.clear
        redirect '/'
    end
end